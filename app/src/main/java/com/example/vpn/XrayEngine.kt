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
 * API: InitCoreEnv, NewCoreController, StartLoop(config, tunFd), StopLoop.
 */
class XrayEngine(
    private val vpnService: VpnService
) {
    companion object {
        private const val TAG = "XrayEngine"

        fun isAvailable(): Boolean {
            return try {
                Class.forName("libv2ray.Libv2ray")
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    private var controller: Any? = null
    private var tunPfd: ParcelFileDescriptor? = null

    fun start(context: Context, xrayJson: String, sessionName: String): Boolean {
        if (!isAvailable()) {
            Log.e(TAG, "libv2ray not on classpath")
            return false
        }

        val assetDir = File(context.filesDir, "xray")
        if (!assetDir.exists()) {
            assetDir.mkdirs()
        }
        File(assetDir, "config.json").writeText(xrayJson)

        return try {
            val lib = Class.forName("libv2ray.Libv2ray")

            invokeStaticNamed(
                lib,
                listOf("InitCoreEnv", "initCoreEnv"),
                arrayOf(assetDir.absolutePath, "")
            )
            Log.i(TAG, "InitCoreEnv OK")

            val pfd = establishTun(sessionName)
            if (pfd == null) {
                Log.e(TAG, "TUN establish failed")
                return false
            }
            tunPfd = pfd
            val fd = pfd.fd
            Log.i(TAG, "TUN fd=$fd")

            val cbIface = Class.forName("libv2ray.CoreCallbackHandler")
            val callback = Proxy.newProxyInstance(
                cbIface.classLoader,
                arrayOf(cbIface)
            ) { _, method, args ->
                when (method.name) {
                    "Startup", "startup" -> {
                        Log.i(TAG, "core Startup")
                        0
                    }
                    "Shutdown", "shutdown" -> {
                        Log.i(TAG, "core Shutdown")
                        0
                    }
                    "OnEmitStatus", "onEmitStatus" -> {
                        Log.i(TAG, "status ${args?.getOrNull(0)}: ${args?.getOrNull(1)}")
                        0
                    }
                    else -> 0
                }
            }

            val ctrl = invokeStaticNamed(
                lib,
                listOf("NewCoreController", "newCoreController"),
                arrayOf(callback)
            ) ?: run {
                Log.e(TAG, "NewCoreController returned null")
                return false
            }
            controller = ctrl

            val startErr = invokeInstanceNamed(
                ctrl,
                listOf("StartLoop", "startLoop"),
                arrayOf(xrayJson, Integer.valueOf(fd))
            )
            if (startErr is Throwable) {
                throw startErr
            }
            if (startErr is String && startErr.isNotBlank() &&
                !startErr.equals("null", ignoreCase = true)
            ) {
                Log.w(TAG, "StartLoop returned: $startErr")
            }

            Log.i(TAG, "StartLoop OK")
            true
        } catch (e: Throwable) {
            val cause = e.cause ?: e
            Log.e(TAG, "XrayEngine.start failed: ${cause.message}", cause)
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
                .setBlocking(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                b.setMetered(false)
            }
            try {
                b.addDisallowedApplication(vpnService.packageName)
            } catch (_: Exception) {
            }
            b.establish()
        } catch (e: Exception) {
            Log.e(TAG, "establishTun", e)
            null
        }
    }

    fun stop() {
        try {
            val c = controller
            if (c != null) {
                invokeInstanceNamed(c, listOf("StopLoop", "stopLoop"), emptyArray())
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

    private fun invokeStaticNamed(clazz: Class<*>, names: List<String>, args: Array<Any?>): Any? {
        for (name in names) {
            for (m in clazz.methods) {
                if (m.name != name) continue
                if (!matchesArgs(m, args)) continue
                return try {
                    m.invoke(null, *coerceArgs(m, args))
                } catch (e: Exception) {
                    throw e.cause ?: e
                }
            }
        }
        Log.w(TAG, "static method not found: $names")
        return null
    }

    private fun invokeInstanceNamed(target: Any, names: List<String>, args: Array<Any?>): Any? {
        val clazz = target.javaClass
        for (name in names) {
            for (m in clazz.methods) {
                if (m.name != name) continue
                if (!matchesArgs(m, args)) continue
                return try {
                    m.invoke(target, *coerceArgs(m, args))
                } catch (e: Exception) {
                    throw e.cause ?: e
                }
            }
        }
        // try zero-arg if args empty path already failed
        if (args.isEmpty()) {
            for (name in names) {
                for (m in clazz.methods) {
                    if (m.name == name && m.parameterTypes.isEmpty()) {
                        return m.invoke(target)
                    }
                }
            }
        }
        Log.w(TAG, "instance method not found: $names")
        return null
    }

    private fun matchesArgs(m: Method, args: Array<Any?>): Boolean {
        val types = m.parameterTypes
        if (types.size != args.size) return false
        for (i in types.indices) {
            val a = args[i] ?: continue
            val t = types[i]
            if (t.isPrimitive) {
                when (t) {
                    Integer.TYPE -> if (a !is Number) return false
                    java.lang.Long.TYPE -> if (a !is Number) return false
                    java.lang.Boolean.TYPE -> if (a !is Boolean) return false
                    else -> {}
                }
            } else if (!t.isAssignableFrom(a.javaClass)) {
                // allow Integer for int boxed later
                if (!(t == Integer.TYPE && a is Number) && !(t == Int::class.javaObjectType && a is Number)) {
                    if (!(CharSequence::class.java.isAssignableFrom(t) && a is String)) {
                        // soft match: String always ok for Object
                        if (t != Any::class.java && t != Object::class.java) {
                            // keep trying other overloads
                        }
                    }
                }
            }
        }
        return true
    }

    private fun coerceArgs(m: Method, args: Array<Any?>): Array<Any?> {
        val types = m.parameterTypes
        return Array(args.size) { i ->
            val a = args[i]
            val t = types[i]
            when {
                a == null -> null
                t == Integer.TYPE || t == Int::class.javaObjectType -> (a as Number).toInt()
                t == java.lang.Long.TYPE || t == Long::class.javaObjectType -> (a as Number).toLong()
                else -> a
            }
        }
    }
}
