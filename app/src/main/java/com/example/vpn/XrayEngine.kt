package com.example.vpn

import android.content.Context
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.lang.reflect.Proxy

/**
 * Xray via AndroidLibXrayLite (libv2ray).
 *
 * Official API (2dust):
 *  - InitCoreEnv(envPath, xudpKey)
 *  - NewCoreController(CoreCallbackHandler)
 *  - StartLoop(configContent, tunFd)  // tunFd=0 → no TUN
 *  - StopLoop()
 */
class XrayEngine(
    private val vpnService: VpnService
) {
    companion object {
        private const val TAG = "XrayEngine"

        fun isAvailable(): Boolean = try {
            Class.forName("libv2ray.Libv2ray")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    private var controller: Any? = null
    private var tunPfd: ParcelFileDescriptor? = null

    /**
     * @return true if core accepted config and is running
     */
    fun start(context: Context, xrayJson: String, sessionName: String): Boolean {
        if (!isAvailable()) {
            Log.e(TAG, "libv2ray not on classpath")
            return false
        }

        val assetDir = File(context.filesDir, "xray").apply { mkdirs() }
        File(assetDir, "config.json").writeText(xrayJson)

        return try {
            val lib = Class.forName("libv2ray.Libv2ray")

            // InitCoreEnv(envPath, key)
            val init = lib.methods.first {
                it.name == "initCoreEnv" || it.name == "InitCoreEnv"
            }
            when (init.parameterTypes.size) {
                2 -> init.invoke(null, assetDir.absolutePath, "")
                1 -> init.invoke(null, assetDir.absolutePath)
                else -> init.invoke(null, *Array(init.parameterTypes.size) { i ->
                    if (i == 0) assetDir.absolutePath else ""
                })
            }
            Log.i(TAG, "InitCoreEnv OK")

            // TUN first – fd passed into StartLoop
            val pfd = establishTun(sessionName) ?: run {
                Log.e(TAG, "TUN establish failed")
                return false
            }
            tunPfd = pfd
            val fd = pfd.fd
            Log.i(TAG, "TUN fd=$fd")

            // CoreCallbackHandler
            val cbIface = Class.forName("libv2ray.CoreCallbackHandler")
            val callback = Proxy.newProxyInstance(
                cbIface.classLoader,
                arrayOf(cbIface)
            ) { _, method, args ->
                when (method.name) {
                    "startup", "Startup" -> {
                        Log.i(TAG, "core Startup")
                        0
                    }
                    "shutdown", "Shutdown" -> {
                        Log.i(TAG, "core Shutdown")
                        0
                    }
                    "onEmitStatus", "OnEmitStatus" -> {
                        Log.i(TAG, "status ${args?.getOrNull(0)}: ${args?.getOrNull(1)}")
                        0
                    }
                    else -> 0
                }
            }

            // NewCoreController(handler)
            val newCtrl = lib.methods.first {
                it.name == "newCoreController" || it.name == "NewCoreController"
            }
            val ctrl = newCtrl.invoke(null, callback)
            controller = ctrl

            // StartLoop(configContent, tunFd)
            val startLoop = ctrl.javaClass.methods.first {
                it.name == "startLoop" || it.name == "StartLoop"
            }
            val result = when (startLoop.parameterTypes.size) {
                2 -> startLoop.invoke(ctrl, xrayJson, fd)
                1 -> startLoop.invoke(ctrl, xrayJson)
                else -> startLoop.invoke(ctrl, xrayJson, fd)
            }

            // Go methods returning error become Exception or String in gomobile
            if (result is Exception) throw result
            if (result is Throwable) throw result
            if (result is String && result.isNotBlank() &&
                !result.equals("null", true) &&
                result.contains("error", true)
            ) {
                throw RuntimeException(result)
            }

            val running = runCatching {
                val f = ctrl.javaClass.methods.firstOrNull {
                    it.name.equals("getIsRunning", true) || it.name == "isRunning"
                }
                (f?.invoke(ctrl) as? Boolean) ?: true
            }.getOrDefault(true)

            Log.i(TAG, "StartLoop done running=$running result=$result")
            running
        } catch (e: Exception) {
            Log.e(TAG, "XrayEngine.start failed", e)
            // unwrap InvocationTargetException
            val cause = e.cause ?: e
            Log.e(TAG, "cause: ${cause.message}", cause)
            stop()
            false
        }
    }

    fun establishTun(sessionName: String): ParcelFileDescriptor? {
        return try {
            tunPfd?.close()
            val b = vpnService.Builder()
                .setSession(sessionName)
                .setMtu(1500)
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
                .setBlocking(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                b.setMetered(false)
            }
            // Critical: Xray process sockets must leave the VPN
            runCatching { b.addDisallowedApplication(vpnService.packageName) }
            b.establish()
        } catch (e: Exception) {
            Log.e(TAG, "establishTun", e)
            null
        }
    }

    fun queryStats(tag: String, direction: String): Long {
        val c = controller ?: return 0L
        return try {
            val m = c.javaClass.methods.firstOrNull {
                it.name.equals("queryStats", true) && it.parameterTypes.size == 2
            } ?: return 0L
            (m.invoke(c, tag, direction) as? Number)?.toLong() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun stop() {
        try {
            controller?.let { c ->
                c.javaClass.methods.firstOrNull {
                    it.name.equals("stopLoop", true) && it.parameterTypes.isEmpty()
                }?.invoke(c)
            }
        } catch (e: Exception) {
            Log.w(TAG, "StopLoop: ${e.message}")
        }
        controller = null
        try {
            tunPfd?.close()
        } catch (_: Exception) {
        }
        tunPfd = null
    }
}
