package com.example.vpn

import android.content.Context
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Bridges to official sing-box **libbox** (package io.nekohasekai.libbox) when the AAR is on the classpath.
 *
 * Place `app/libs/libbox.aar` (built via scripts/build-libbox.sh or CI) then rebuild.
 * Uses reflection so the project still compiles without the AAR; runtime requires it for real proxying.
 */
class LibboxEngine(
    private val vpnService: VpnService,
    private val onTunOpened: (ParcelFileDescriptor) -> Unit
) {
    companion object {
        private const val TAG = "LibboxEngine"

        fun isAvailable(): Boolean = try {
            Class.forName("io.nekohasekai.libbox.Libbox")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    private var boxService: Any? = null
    private var commandServer: Any? = null
    private var tunPfd: ParcelFileDescriptor? = null

    /**
     * Start sing-box with a full JSON config (must include tun inbound or let libbox request TUN via platform).
     * Preferred: config from [SingBoxConfigBuilder.buildFromShareLink].
     */
    fun start(context: Context, configJson: String): Boolean {
        if (!isAvailable()) {
            Log.e(TAG, "libbox AAR not on classpath – add app/libs/libbox.aar")
            return false
        }
        return try {
            val baseDir = File(context.filesDir, "libbox").apply { mkdirs() }
            val workDir = File(baseDir, "run").apply { mkdirs() }
            val tempDir = File(baseDir, "tmp").apply { mkdirs() }

            // Libbox.setup(basePath, workingPath, tempPath, fixAndroidStack)
            val libbox = Class.forName("io.nekohasekai.libbox.Libbox")
            val setup = libbox.methods.firstOrNull {
                it.name == "setup" && it.parameterTypes.size >= 3
            }
            if (setup != null) {
                when (setup.parameterTypes.size) {
                    3 -> setup.invoke(null, baseDir.absolutePath, workDir.absolutePath, tempDir.absolutePath)
                    4 -> setup.invoke(null, baseDir.absolutePath, workDir.absolutePath, tempDir.absolutePath, true)
                    else -> setup.invoke(null, *Array(setup.parameterTypes.size) { i ->
                        when (i) {
                            0 -> baseDir.absolutePath
                            1 -> workDir.absolutePath
                            2 -> tempDir.absolutePath
                            else -> true
                        }
                    })
                }
                Log.i(TAG, "Libbox.setup OK")
            }

            val platformIface = Class.forName("io.nekohasekai.libbox.PlatformInterface")
            val platform = Proxy.newProxyInstance(
                platformIface.classLoader,
                arrayOf(platformIface),
                PlatformHandler()
            )

            // Prefer CommandServer(handler, platform) if present
            val commandServerClass = Class.forName("io.nekohasekai.libbox.CommandServer")
            val ctor = commandServerClass.constructors.maxByOrNull { it.parameterTypes.size }
                ?: error("CommandServer ctor missing")

            val handlerIface = try {
                Class.forName("io.nekohasekai.libbox.CommandServerHandler")
            } catch (_: ClassNotFoundException) {
                null
            }

            val server = if (handlerIface != null && ctor.parameterTypes.size >= 2) {
                val handler = Proxy.newProxyInstance(
                    handlerIface.classLoader,
                    arrayOf(handlerIface),
                    CommandHandler()
                )
                when (ctor.parameterTypes.size) {
                    2 -> ctor.newInstance(handler, platform)
                    else -> ctor.newInstance(*Array(ctor.parameterTypes.size) { idx ->
                        when {
                            ctor.parameterTypes[idx].isAssignableFrom(handlerIface) -> handler
                            ctor.parameterTypes[idx].isAssignableFrom(platformIface) -> platform
                            else -> null
                        }
                    })
                }
            } else {
                ctor.newInstance(*Array(ctor.parameterTypes.size) { platform })
            }

            // commandServer.start()
            commandServerClass.methods.firstOrNull { it.name == "start" && it.parameterTypes.isEmpty() }
                ?.invoke(server)

            // startOrReloadService(content) or startOrReloadService(content, OverrideOptions)
            val reload = commandServerClass.methods.filter { it.name == "startOrReloadService" }
            val started = when {
                reload.any { it.parameterTypes.size == 1 } -> {
                    reload.first { it.parameterTypes.size == 1 }.invoke(server, configJson)
                    true
                }
                reload.any { it.parameterTypes.size == 2 } -> {
                    val m = reload.first { it.parameterTypes.size == 2 }
                    val optClass = m.parameterTypes[1]
                    val opts = optClass.getDeclaredConstructor().newInstance()
                    m.invoke(server, configJson, opts)
                    true
                }
                else -> {
                    // Fallback: BoxService style
                    startBoxServiceFallback(configJson, platform)
                }
            }

            commandServer = server
            Log.i(TAG, "libbox service started=$started")
            started
        } catch (e: Exception) {
            Log.e(TAG, "LibboxEngine.start failed", e)
            false
        }
    }

    private fun startBoxServiceFallback(configJson: String, platform: Any): Boolean {
        return try {
            val boxClass = Class.forName("io.nekohasekai.libbox.BoxService")
            // try new BoxService(config, platform)
            val instance = boxClass.constructors.firstOrNull { it.parameterTypes.size >= 1 }
                ?.let { c ->
                    when (c.parameterTypes.size) {
                        1 -> c.newInstance(configJson)
                        2 -> c.newInstance(configJson, platform)
                        else -> c.newInstance(*Array(c.parameterTypes.size) { i ->
                            if (i == 0) configJson else platform
                        })
                    }
                } ?: return false
            boxClass.methods.firstOrNull { it.name == "start" && it.parameterTypes.isEmpty() }
                ?.invoke(instance)
            boxService = instance
            true
        } catch (e: Exception) {
            Log.e(TAG, "BoxService fallback failed", e)
            false
        }
    }

    fun stop() {
        try {
            commandServer?.let { s ->
                val c = s.javaClass
                c.methods.firstOrNull { it.name == "closeService" && it.parameterTypes.isEmpty() }?.invoke(s)
                c.methods.firstOrNull { it.name == "close" && it.parameterTypes.isEmpty() }?.invoke(s)
            }
            boxService?.let { s ->
                s.javaClass.methods.firstOrNull { it.name == "close" }?.invoke(s)
            }
        } catch (e: Exception) {
            Log.w(TAG, "stop: ${e.message}")
        }
        commandServer = null
        boxService = null
        try {
            tunPfd?.close()
        } catch (_: Exception) {
        }
        tunPfd = null
    }

    /**
     * Called from PlatformInterface.openTun via proxy when libbox requests a TUN fd.
     */
    fun openTunFromOptions(options: Any?): Int {
        val builder = vpnService.Builder()
            .setSession("IFIX · sing-box")
            .setMtu(extractMtu(options))
            .addAddress("172.19.0.1", 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("1.1.1.1")
            .setBlocking(false)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        runCatching {
            builder.addDisallowedApplication(vpnService.packageName)
        }

        val pfd = builder.establish()
            ?: error("android: VPN not prepared or revoked")
        tunPfd = pfd
        onTunOpened(pfd)
        Log.i(TAG, "openTun fd=${pfd.fd}")
        return pfd.fd
    }

    private fun extractMtu(options: Any?): Int {
        if (options == null) return 1500
        return try {
            val m = options.javaClass.methods.firstOrNull {
                it.name == "getMtu" || it.name == "mtu"
            }
            (m?.invoke(options) as? Number)?.toInt() ?: 1500
        } catch (_: Exception) {
            1500
        }
    }

    private inner class PlatformHandler : InvocationHandler {
        override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
            val name = method.name
            val a = args ?: emptyArray()
            return when (name) {
                "usePlatformAutoDetectInterfaceControl" -> true
                "autoDetectInterfaceControl" -> {
                    val fd = (a.getOrNull(0) as? Number)?.toInt() ?: return null
                    vpnService.protect(fd)
                    null
                }
                "openTun" -> openTunFromOptions(a.getOrNull(0))
                "useProcFS" -> android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q
                "underNetworkExtension" -> false
                "includeAllNetworks" -> false
                "clearDNSCache" -> null
                "usePlatformShell" -> false
                "usePlatformBridge" -> false
                "localDNSTransport" -> null
                "readWIFIState" -> null
                "getInterfaces" -> emptyInterfaceIterator()
                "findConnectionOwner" -> error("not implemented")
                "startDefaultInterfaceMonitor", "closeDefaultInterfaceMonitor" -> null
                "startNeighborMonitor", "closeNeighborMonitor" -> null
                "sendNotification" -> null
                "registerMyInterface" -> null
                "toString" -> "IfixPlatformInterface"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === a.getOrNull(0)
                else -> {
                    Log.d(TAG, "PlatformInterface.$name ignored")
                    defaultValue(method.returnType)
                }
            }
        }
    }

    private inner class CommandHandler : InvocationHandler {
        override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
            when (method.name) {
                "serviceStop" -> stop()
                "serviceReload" -> Log.i(TAG, "serviceReload requested")
                else -> Log.d(TAG, "CommandServerHandler.${method.name}")
            }
            return defaultValue(method.returnType)
        }
    }

    private fun emptyInterfaceIterator(): Any? {
        return try {
            val iterClass = Class.forName("io.nekohasekai.libbox.NetworkInterfaceIterator")
            Proxy.newProxyInstance(iterClass.classLoader, arrayOf(iterClass)) { _, m, _ ->
                when (m.name) {
                    "hasNext" -> false
                    "next" -> null
                    else -> defaultValue(m.returnType)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE, Boolean::class.java -> false
        java.lang.Integer.TYPE, Int::class.java -> 0
        java.lang.Long.TYPE, Long::class.java -> 0L
        java.lang.Float.TYPE, Float::class.java -> 0f
        java.lang.Double.TYPE, Double::class.java -> 0.0
        java.lang.Void.TYPE, Void::class.java -> null
        String::class.java -> ""
        else -> null
    }
}
