package com.example.vpn

import android.content.Context
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Xray via AndroidLibXrayLite (libv2ray).
 * Matches v2rayNG: Seq.setContext → InitCoreEnv → NewCoreController → StartLoop(config, tunFd).
 */
class XrayEngine(
    private val vpnService: VpnService
) {
    companion object {
        private const val TAG = "XrayEngine"

        @Volatile
        private var envReady = false

        fun isAvailable(): Boolean = try {
            Class.forName("libv2ray.Libv2ray")
            true
        } catch (_: Throwable) {
            false
        }

        /** Must run once with Application/Service context before any core call. */
        fun ensureEnv(context: Context): String? {
            if (envReady) return null
            return try {
                // gomobile requires Seq.setContext on Android
                try {
                    val seq = Class.forName("go.Seq")
                    seq.getMethod("setContext", Context::class.java)
                        .invoke(null, context.applicationContext)
                    Log.i(TAG, "go.Seq.setContext OK")
                } catch (e: Throwable) {
                    Log.w(TAG, "Seq.setContext skipped: ${e.message}")
                }

                val assetDir = File(context.filesDir, "xray").apply { mkdirs() }
                // Touch placeholder so path exists; geoip not required when routing avoids geoip:
                File(assetDir, ".keep").writeText("1")

                val lib = Class.forName("libv2ray.Libv2ray")
                val init = findStatic(lib, listOf("InitCoreEnv", "initCoreEnv"), 2)
                    ?: return "InitCoreEnv not found in libv2ray"
                init.invoke(null, assetDir.absolutePath, "")
                envReady = true
                Log.i(TAG, "InitCoreEnv OK path=${assetDir.absolutePath}")
                null
            } catch (e: Throwable) {
                val c = e.cause ?: e
                Log.e(TAG, "ensureEnv failed", c)
                c.message ?: c.javaClass.simpleName
            }
        }

        private fun findStatic(clazz: Class<*>, names: List<String>, argc: Int): Method? {
            for (name in names) {
                for (m in clazz.methods) {
                    if (m.name == name && m.parameterTypes.size == argc) return m
                }
            }
            return null
        }
    }

    private var controller: Any? = null
    private var tunPfd: ParcelFileDescriptor? = null
    var lastError: String? = null
        private set

    fun start(context: Context, xrayJson: String, sessionName: String): Boolean {
        lastError = null
        if (!isAvailable()) {
            lastError = "libv2ray.aar در APK نیست"
            return false
        }

        ensureEnv(context)?.let {
            lastError = "InitCoreEnv: $it"
            return false
        }

        return try {
            File(context.filesDir, "xray/config.json").writeText(xrayJson)

            val pfd = establishTun(sessionName)
            if (pfd == null) {
                lastError = "ایجاد TUN ناموفق (مجوز VPN؟)"
                return false
            }
            tunPfd = pfd
            val fd = pfd.fd
            Log.i(TAG, "TUN fd=$fd session=$sessionName")

            val cbIface = Class.forName("libv2ray.CoreCallbackHandler")
            val callback = Proxy.newProxyInstance(
                cbIface.classLoader,
                arrayOf(cbIface)
            ) { _, method, args ->
                // gomobile maps Go int → Java long
                when (method.name) {
                    "Startup", "startup" -> {
                        Log.i(TAG, "core Startup")
                        0L
                    }
                    "Shutdown", "shutdown" -> {
                        Log.i(TAG, "core Shutdown")
                        0L
                    }
                    "OnEmitStatus", "onEmitStatus" -> {
                        Log.i(TAG, "status ${args?.getOrNull(0)}: ${args?.getOrNull(1)}")
                        0L
                    }
                    else -> {
                        when (method.returnType) {
                            java.lang.Long.TYPE, Long::class.javaObjectType -> 0L
                            Integer.TYPE, Int::class.javaObjectType -> 0
                            java.lang.Boolean.TYPE -> false
                            else -> null
                        }
                    }
                }
            }

            val lib = Class.forName("libv2ray.Libv2ray")
            val newCtrl = findStatic(lib, listOf("NewCoreController", "newCoreController"), 1)
                ?: run {
                    lastError = "NewCoreController not found"
                    return false
                }
            val ctrl = newCtrl.invoke(null, callback)
                ?: run {
                    lastError = "NewCoreController returned null"
                    return false
                }
            controller = ctrl

            // StartLoop(config string, tunFd int32) — Java: (String, int)
            val startLoop = findInstance(ctrl, listOf("StartLoop", "startLoop"), 2)
                ?: run {
                    lastError = "StartLoop method not found on CoreController"
                    dumpMethods(ctrl)
                    return false
                }

            Log.i(TAG, "StartLoop sig=${startLoop.parameterTypes.joinToString { it.simpleName }}")
            try {
                // pass primitive int for tunFd
                startLoop.invoke(ctrl, xrayJson, fd)
            } catch (e: Exception) {
                val c = e.cause ?: e
                lastError = "StartLoop: ${c.message ?: c.javaClass.simpleName}"
                Log.e(TAG, "StartLoop threw", c)
                stop()
                return false
            }

            Log.i(TAG, "StartLoop OK")
            true
        } catch (e: Throwable) {
            val c = e.cause ?: e
            lastError = c.message ?: c.javaClass.simpleName
            Log.e(TAG, "XrayEngine.start failed", c)
            stop()
            false
        }
    }

    fun establishTun(sessionName: String): ParcelFileDescriptor? {
        return try {
            try {
                tunPfd?.close()
            } catch (_: Exception) {
            }
            val b = vpnService.Builder()
                .setSession(sessionName)
                .setMtu(1500)
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
                .setBlocking(true) // blocking TUN is more reliable with xray
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                b.setMetered(false)
            }
            // Prevent VPN loop for our own sockets (outbound to proxy server)
            try {
                b.addDisallowedApplication(vpnService.packageName)
            } catch (_: Exception) {
            }
            b.establish()
        } catch (e: Exception) {
            Log.e(TAG, "establishTun", e)
            lastError = "TUN: ${e.message}"
            null
        }
    }

    fun stop() {
        try {
            val c = controller
            if (c != null) {
                findInstance(c, listOf("StopLoop", "stopLoop"), 0)?.invoke(c)
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

    private fun findStatic(clazz: Class<*>, names: List<String>, argc: Int): Method? {
        for (name in names) {
            for (m in clazz.methods) {
                if (m.name == name && m.parameterTypes.size == argc) return m
            }
        }
        return null
    }

    private fun findInstance(target: Any, names: List<String>, argc: Int): Method? {
        val clazz = target.javaClass
        for (name in names) {
            for (m in clazz.methods) {
                if (m.name == name && m.parameterTypes.size == argc) return m
            }
        }
        return null
    }

    private fun dumpMethods(target: Any) {
        Log.w(TAG, "Methods on ${target.javaClass.name}:")
        target.javaClass.methods.forEach { m ->
            Log.w(TAG, "  ${m.name}(${m.parameterTypes.joinToString { it.simpleName }})")
        }
    }
}
