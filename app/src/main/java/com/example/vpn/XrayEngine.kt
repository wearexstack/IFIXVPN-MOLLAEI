package com.example.vpn

import android.content.Context
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.reflect.Method

/**
 * Xray-core integration for IFIX VPN.
 *
 * Supports (in order):
 * 1) libv2ray / AndroidLibXrayLite on classpath (`go.Seq` + `libv2ray.CoreController` style)
 * 2) Native `libxray.so` + point to binary if packaged under nativeLibraryDir
 * 3) Explicit failure if nothing is available
 *
 * Place AAR/SO under `app/libs/` or `jniLibs` — see `app/libs/README.md`.
 */
class XrayEngine(
    private val vpnService: VpnService
) {
    companion object {
        private const val TAG = "XrayEngine"
        const val SOCKS_HOST = "127.0.0.1"
        const val SOCKS_PORT = 10808

        fun isLibV2RayAvailable(): Boolean = try {
            Class.forName("libv2ray.Libv2ray")
            true
        } catch (_: ClassNotFoundException) {
            try {
                Class.forName("com.github.2dust.v2ray.core.V2RayVPNServiceSupportsSet")
                true
            } catch (_: ClassNotFoundException) {
                false
            }
        }
    }

    private var coreController: Any? = null
    private var process: Process? = null
    private var tunPfd: ParcelFileDescriptor? = null

    fun start(context: Context, xrayJson: String): Boolean {
        val dir = File(context.filesDir, "xray").apply { mkdirs() }
        val configFile = File(dir, "config.json").apply { writeText(xrayJson) }
        Log.i(TAG, "Xray config written (${xrayJson.length} bytes) → ${configFile.absolutePath}")

        // 1) libv2ray gomobile binding
        if (startLibV2Ray(context, configFile.absolutePath, xrayJson)) {
            return true
        }

        // 2) Standalone xray binary in native libs / filesDir
        if (startXrayProcess(context, configFile)) {
            return true
        }

        Log.e(TAG, "No Xray core found. Add libv2ray AAR or xray binary. See app/libs/README.md")
        return false
    }

    private fun startLibV2Ray(context: Context, configPath: String, configContent: String): Boolean {
        return try {
            // Common pattern from AndroidLibXrayLite / v2rayNG forks:
            // Libv2ray.initV2Env(assetPath) / CoreController.startLoop(config)
            val libClass = runCatching { Class.forName("libv2ray.Libv2ray") }.getOrNull()
            if (libClass != null) {
                // init environment
                val assetPath = context.filesDir.absolutePath
                invokeStatic(libClass, "initV2Env", arrayOf(assetPath), arrayOf(String::class.java))
                    ?: invokeStatic(libClass, "InitCoreEnv", arrayOf(assetPath), arrayOf(String::class.java))

                // Try CoreController
                val controllerClass = runCatching {
                    Class.forName("libv2ray.CoreController")
                }.getOrNull()

                if (controllerClass != null) {
                    val supports = buildSupportsSet()
                    val ctor = controllerClass.constructors.firstOrNull()
                    val controller = when {
                        ctor == null -> null
                        ctor.parameterTypes.isEmpty() -> ctor.newInstance()
                        else -> ctor.newInstance(*Array(ctor.parameterTypes.size) { supports })
                    }
                    if (controller != null) {
                        val started = invokeInstance(
                            controller,
                            listOf("startLoop", "StartLoop", "start"),
                            configContent
                        ) || invokeInstance(
                            controller,
                            listOf("startLoop", "StartLoop"),
                            configPath
                        )
                        if (started) {
                            coreController = controller
                            protectLocalSockets()
                            Log.i(TAG, "libv2ray CoreController started")
                            return true
                        }
                    }
                }

                // Libv2ray.runConfigFromFile / measureOutboundDelay style
                val runOk = invokeStatic(
                    libClass,
                    "runConfigFromFile",
                    arrayOf(configPath),
                    arrayOf(String::class.java)
                ) != null || invokeStatic(
                    libClass,
                    "RunConfig",
                    arrayOf(configContent),
                    arrayOf(String::class.java)
                ) != null
                if (runOk) {
                    protectLocalSockets()
                    Log.i(TAG, "libv2ray runConfig started")
                    return true
                }
            }

            // v2rayNG-style Point.getInstance
            val pointClass = runCatching { Class.forName("com.v2ray.ang.service.V2RayServiceManager") }.getOrNull()
            if (pointClass != null) {
                Log.i(TAG, "V2RayServiceManager present but requires full v2rayNG wiring")
            }

            false
        } catch (e: Exception) {
            Log.e(TAG, "startLibV2Ray failed", e)
            false
        }
    }

    private fun buildSupportsSet(): Any? {
        // Optional callback interface for VPN protect(fd)
        return try {
            val iface = Class.forName("libv2ray.V2RayVPNServiceSupportsSet")
            java.lang.reflect.Proxy.newProxyInstance(
                iface.classLoader,
                arrayOf(iface)
            ) { _, method, args ->
                when (method.name) {
                    "protect", "onProtect" -> {
                        val fd = (args?.getOrNull(0) as? Number)?.toInt() ?: return@newProxyInstance true
                        vpnService.protect(fd)
                    }
                    "shutdown", "prepare", "onEmitStatus" -> true
                    "setup" -> ""
                    else -> defaultValue(method.returnType)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun startXrayProcess(context: Context, configFile: File): Boolean {
        val candidates = listOf(
            File(context.applicationInfo.nativeLibraryDir, "libxray.so"),
            File(context.applicationInfo.nativeLibraryDir, "xray"),
            File(context.filesDir, "xray/xray"),
            File(context.filesDir, "xray")
        )
        val bin = candidates.firstOrNull { it.exists() && it.canExecute() || it.exists() && it.name.endsWith(".so") }
            ?: return false

        return try {
            // Make executable if needed
            if (!bin.canExecute()) bin.setExecutable(true)

            val pb = ProcessBuilder(bin.absolutePath, "run", "-c", configFile.absolutePath)
                .directory(configFile.parentFile)
                .redirectErrorStream(true)
            val env = pb.environment()
            env["XRAY_LOCATION_ASSET"] = configFile.parentFile?.absolutePath ?: context.filesDir.absolutePath

            process = pb.start()
            Thread {
                try {
                    BufferedReader(InputStreamReader(process!!.inputStream)).use { r ->
                        var line: String?
                        while (r.readLine().also { line = it } != null) {
                            Log.d(TAG, "xray: $line")
                        }
                    }
                } catch (_: Exception) {
                }
            }.start()

            // Give core a moment
            Thread.sleep(400)
            if (process?.isAlive != true) {
                Log.e(TAG, "xray process exited early")
                return false
            }
            protectLocalSockets()
            Log.i(TAG, "xray process started pid=${process?.hashCode()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "startXrayProcess failed", e)
            false
        }
    }

    /** Protect loopback SOCKS so Xray can reach the internet outside the VPN. */
    private fun protectLocalSockets() {
        // Best-effort: individual sockets must call protect; binary cores often use protect callback.
        Log.d(TAG, "protectLocalSockets – rely on VPN protect() callback / disallow app package")
    }

    /**
     * Establish system TUN. Packet path to Xray SOCKS still needs tun2socks (not bundled here).
     * When only SOCKS inbound is used, traffic is proxied for apps that use the local SOCKS,
     * or when a tun2socks bridge is added later.
     */
    fun establishTun(sessionName: String): ParcelFileDescriptor? {
        return try {
            tunPfd?.close()
            val builder = vpnService.Builder()
                .setSession(sessionName)
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
                .setBlocking(false)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }
            // Keep our process (Xray) off the VPN so it can dial the node
            runCatching { builder.addDisallowedApplication(vpnService.packageName) }
            val pfd = builder.establish()
            tunPfd = pfd
            Log.i(TAG, "TUN established fd=${pfd?.fd}")
            pfd
        } catch (e: Exception) {
            Log.e(TAG, "establishTun failed", e)
            null
        }
    }

    fun stop() {
        try {
            coreController?.let { c ->
                invokeInstance(c, listOf("stopLoop", "StopLoop", "stop", "close"), null)
            }
        } catch (_: Exception) {
        }
        coreController = null
        try {
            process?.destroy()
        } catch (_: Exception) {
        }
        process = null
        try {
            tunPfd?.close()
        } catch (_: Exception) {
        }
        tunPfd = null
    }

    private fun invokeStatic(
        clazz: Class<*>,
        name: String,
        args: Array<Any?>,
        types: Array<Class<*>>
    ): Any? {
        return try {
            val m: Method = clazz.getMethod(name, *types)
            m.invoke(null, *args)
        } catch (_: Exception) {
            null
        }
    }

    private fun invokeInstance(target: Any, names: List<String>, arg: String?): Boolean {
        for (n in names) {
            try {
                if (arg == null) {
                    target.javaClass.methods.firstOrNull {
                        it.name.equals(n, true) && it.parameterTypes.isEmpty()
                    }?.invoke(target) ?: continue
                } else {
                    target.javaClass.methods.firstOrNull {
                        it.name.equals(n, true) &&
                            it.parameterTypes.size == 1 &&
                            it.parameterTypes[0] == String::class.java
                    }?.invoke(target, arg) ?: continue
                }
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE, Boolean::class.java -> true
        java.lang.Integer.TYPE, Int::class.java -> 0
        java.lang.Long.TYPE, Long::class.java -> 0L
        String::class.java -> ""
        else -> null
    }
}
