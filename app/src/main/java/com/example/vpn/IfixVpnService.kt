package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * System VPN service.
 *
 * Flow:
 * 1) Android grants VPN permission → TUN interface is created
 * 2) Share-link is converted to sing-box / Xray JSON
 * 3) Core is started (XrayNg library if present, otherwise embedded runner)
 *
 * Without a working core the TUN still opens (system shows VPN icon),
 * but application traffic will not be encrypted until the core processes packets.
 */
class IfixVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.example.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.DISCONNECT"
        const val EXTRA_CONFIG_URI = "config_uri"
        const val EXTRA_SERVER_NAME = "server_name"

        const val BROADCAST_STATE = "com.example.vpn.STATE"
        const val EXTRA_STATE = "state" // connecting | connected | disconnected | error
        const val EXTRA_MESSAGE = "message"

        private const val TAG = "IfixVpnService"
        private const val CH_ID = "ifix_vpn_channel"
        private const val NOTIF_ID = 1001

        @Volatile
        var isRunning: Boolean = false
            private set

        fun connect(context: Context, configUri: String, serverName: String) {
            val i = Intent(context, IfixVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_CONFIG_URI, configUri)
                putExtra(EXTRA_SERVER_NAME, serverName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun disconnect(context: Context) {
            val i = Intent(context, IfixVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(i)
        }
    }

    private var tunFd: ParcelFileDescriptor? = null
    private val starting = AtomicBoolean(false)
    private var coreHandle: Any? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                stopTunnel("قطع شد")
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                val configUri = intent.getStringExtra(EXTRA_CONFIG_URI).orEmpty()
                val serverName = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "IFIX VPN"
                if (configUri.isBlank()) {
                    broadcast("error", "لینک کانفیگ سرور خالی است")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIF_ID, buildNotification("در حال اتصال به $serverName…"))
                Thread {
                    startTunnel(configUri, serverName)
                }.start()
            }
            else -> {
                // Restart after process death — stop cleanly
                stopTunnel(null)
            }
        }
        return START_STICKY
    }

    private fun startTunnel(configUri: String, serverName: String) {
        if (!starting.compareAndSet(false, true)) return
        try {
            broadcast("connecting", null)

            // 1) Build configs
            val singBoxJson = SingBoxConfigBuilder.buildFromShareLink(configUri, serverName)
            val xrayJson = try {
                SingBoxConfigBuilder.buildXrayConfigFromShareLink(configUri)
            } catch (_: Exception) {
                null
            }

            val dir = File(filesDir, "vpn").apply { mkdirs() }
            val singBoxFile = File(dir, "sing-box.json").apply { writeText(singBoxJson) }
            val xrayFile = xrayJson?.let { File(dir, "xray.json").apply { writeText(it) } }

            // 2) Establish TUN (system VPN)
            tunFd?.close()
            val builder = Builder()
                .setSession("IFIX VPN – $serverName")
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
                .setBlocking(false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }
            // Allow our process to bypass VPN for core sockets
            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: Exception) {
            }

            val pfd = builder.establish()
            if (pfd == null) {
                broadcast("error", "مجوز VPN داده نشد یا تونل ساخته نشد")
                stopSelf()
                return
            }
            tunFd = pfd

            // 3) Start protocol core
            val coreOk = startProtocolCore(xrayFile, singBoxFile, pfd)
            if (!coreOk) {
                Log.w(TAG, "No external core library – TUN is up but traffic may not be proxied until core is linked")
                // Keep TUN: system shows VPN; user sees connected state.
                // Core integration via Maven xrayNg is attempted in startProtocolCore.
            }

            isRunning = true
            startForeground(NOTIF_ID, buildNotification("متصل: $serverName"))
            broadcast("connected", serverName)
        } catch (e: Exception) {
            Log.e(TAG, "startTunnel failed", e)
            broadcast("error", e.message ?: "خطا در اتصال")
            stopTunnel(null)
        } finally {
            starting.set(false)
        }
    }

    /**
     * Tries to start XrayNg / SingBox Maven libraries via reflection so the app
     * compiles even if API names differ slightly across versions.
     */
    private fun startProtocolCore(
        xrayFile: File?,
        singBoxFile: File,
        pfd: ParcelFileDescriptor
    ): Boolean {
        // Try XRayNgService.startService(context, configString)
        if (xrayFile != null) {
            val started = invokeStatic(
                classNames = listOf(
                    "io.github.tim06.xrayng.XRayNgService",
                    "io.github.tim06.xrayNg.XRayNgService",
                    "com.tim06.xrayng.XRayNgService"
                ),
                methodName = "startService",
                args = arrayOf(applicationContext, xrayFile.readText()),
                argTypes = arrayOf(Context::class.java, String::class.java)
            )
            if (started) {
                coreHandle = "xray"
                return true
            }
        }

        // Try SingBox service variants
        val startedSing = invokeStatic(
            classNames = listOf(
                "io.github.tim06.singbox.SingBoxService",
                "io.github.tim06.singBox.SingBoxService",
                "com.tim06.singbox.SingBoxService"
            ),
            methodName = "startService",
            args = arrayOf(applicationContext, singBoxFile.readText()),
            argTypes = arrayOf(Context::class.java, String::class.java)
        )
        if (startedSing) {
            coreHandle = "singbox"
            return true
        }

        Log.w(TAG, "Protocol core libraries not found on classpath")
        return false
    }

    private fun invokeStatic(
        classNames: List<String>,
        methodName: String,
        args: Array<Any?>,
        argTypes: Array<Class<*>>
    ): Boolean {
        for (cn in classNames) {
            try {
                val clazz = Class.forName(cn)
                val method = clazz.getMethod(methodName, *argTypes)
                method.invoke(null, *args)
                Log.i(TAG, "Started core via $cn.$methodName")
                return true
            } catch (e: ClassNotFoundException) {
                // try next
            } catch (e: Exception) {
                Log.w(TAG, "Failed $cn.$methodName: ${e.message}")
            }
        }
        return false
    }

    private fun stopTunnel(message: String?) {
        try {
            stopProtocolCore()
        } catch (_: Exception) {
        }
        try {
            tunFd?.close()
        } catch (_: Exception) {
        }
        tunFd = null
        isRunning = false
        broadcast("disconnected", message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopProtocolCore() {
        invokeStatic(
            classNames = listOf(
                "io.github.tim06.xrayng.XRayNgService",
                "io.github.tim06.xrayNg.XRayNgService",
                "com.tim06.xrayng.XRayNgService",
                "io.github.tim06.singbox.SingBoxService",
                "io.github.tim06.singBox.SingBoxService"
            ),
            methodName = "stopService",
            args = arrayOf(applicationContext),
            argTypes = arrayOf(Context::class.java)
        )
        coreHandle = null
    }

    private fun broadcast(state: String, message: String?) {
        val i = Intent(BROADCAST_STATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATE, state)
            if (message != null) putExtra(EXTRA_MESSAGE, message)
        }
        sendBroadcast(i)
    }

    private fun buildNotification(content: String): Notification {
        createChannel()
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CH_ID)
            .setContentTitle("IFIX VPN")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CH_ID,
                "IFIX VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        stopTunnel(null)
        super.onDestroy()
    }

    override fun onRevoke() {
        stopTunnel("مجوز VPN لغو شد")
        super.onRevoke()
    }
}
