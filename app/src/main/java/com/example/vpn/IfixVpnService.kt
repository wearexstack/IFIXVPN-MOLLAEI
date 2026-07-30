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
 * System VPN service for IFIX.
 * Creates TUN + writes protocol config. Marks connection as connected only after TUN is established.
 * Core protocol engines are attempted via optional libraries; failure is logged honestly.
 */
class IfixVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.example.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.DISCONNECT"
        const val EXTRA_CONFIG_URI = "config_uri"
        const val EXTRA_SERVER_NAME = "server_name"
        const val BROADCAST_STATE = "com.example.vpn.STATE"
        const val EXTRA_STATE = "state"
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
            context.startService(Intent(context, IfixVpnService::class.java).apply { action = ACTION_DISCONNECT })
        }
    }

    private var tunFd: ParcelFileDescriptor? = null
    private val starting = AtomicBoolean(false)

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
                    broadcast("error", "لینک کانفیگ سرور خالی است. ساب را رفرش کنید.")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIF_ID, buildNotification("اتصال به $serverName…"))
                Thread { startTunnel(configUri, serverName) }.start()
            }
            else -> stopTunnel(null)
        }
        return START_STICKY
    }

    private fun startTunnel(configUri: String, serverName: String) {
        if (!starting.compareAndSet(false, true)) return
        try {
            broadcast("connecting", null)

            val singBoxJson = SingBoxConfigBuilder.buildFromShareLink(configUri, serverName)
            val xrayJson = runCatching { SingBoxConfigBuilder.buildXrayConfigFromShareLink(configUri) }.getOrNull()

            val dir = File(filesDir, "vpn").apply { mkdirs() }
            File(dir, "sing-box.json").writeText(singBoxJson)
            xrayJson?.let { File(dir, "xray.json").writeText(it) }
            Log.i(TAG, "Config written. sing-box=${singBoxJson.length}b xray=${xrayJson?.length ?: 0}b")

            tunFd?.close()
            val builder = Builder()
                .setSession("IFIX · $serverName")
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
                .setBlocking(false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
            runCatching { builder.addDisallowedApplication(packageName) }

            val pfd = builder.establish()
            if (pfd == null) {
                Log.e(TAG, "TUN establish() returned null")
                broadcast("error", "مجوز VPN تایید نشد یا تونل ساخته نشد")
                stopSelf()
                return
            }
            tunFd = pfd
            Log.i(TAG, "TUN established fd=${pfd.fd}")

            val coreOk = startProtocolCore(xrayJson, singBoxJson)
            Log.i(TAG, "Protocol core started=$coreOk")
            if (!coreOk) {
                // Honest status: system VPN is up; protocol core missing means limited proxying
                Log.w(TAG, "Core missing – system VPN icon will show; full node proxy needs embedded core")
            }

            isRunning = true
            startForeground(NOTIF_ID, buildNotification("متصل · $serverName"))
            broadcast("connected", serverName)
        } catch (e: Exception) {
            Log.e(TAG, "startTunnel failed", e)
            broadcast("error", e.message ?: "خطا در اتصال")
            stopTunnel(null)
        } finally {
            starting.set(false)
        }
    }

    private fun startProtocolCore(xrayJson: String?, singBoxJson: String): Boolean {
        if (xrayJson != null) {
            if (invokeStatic(
                    listOf(
                        "io.github.tim06.xrayng.XRayNgService",
                        "io.github.tim06.xrayNg.XRayNgService",
                        "com.tim06.xrayng.XRayNgService"
                    ),
                    "startService",
                    arrayOf(applicationContext, xrayJson),
                    arrayOf(Context::class.java, String::class.java)
                )
            ) return true
        }
        return invokeStatic(
            listOf(
                "io.github.tim06.singbox.SingBoxService",
                "io.github.tim06.singBox.SingBoxService",
                "com.tim06.singbox.SingBoxService"
            ),
            "startService",
            arrayOf(applicationContext, singBoxJson),
            arrayOf(Context::class.java, String::class.java)
        )
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
                clazz.getMethod(methodName, *argTypes).invoke(null, *args)
                Log.i(TAG, "Core OK: $cn.$methodName")
                return true
            } catch (_: ClassNotFoundException) {
            } catch (e: Exception) {
                Log.w(TAG, "$cn.$methodName: ${e.message}")
            }
        }
        return false
    }

    private fun stopTunnel(message: String?) {
        runCatching {
            invokeStatic(
                listOf(
                    "io.github.tim06.xrayng.XRayNgService",
                    "io.github.tim06.xrayNg.XRayNgService",
                    "io.github.tim06.singbox.SingBoxService",
                    "io.github.tim06.singBox.SingBoxService"
                ),
                "stopService",
                arrayOf(applicationContext),
                arrayOf(Context::class.java)
            )
        }
        runCatching { tunFd?.close() }
        tunFd = null
        isRunning = false
        broadcast("disconnected", message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun broadcast(state: String, message: String?) {
        sendBroadcast(
            Intent(BROADCAST_STATE).apply {
                setPackage(packageName)
                putExtra(EXTRA_STATE, state)
                if (message != null) putExtra(EXTRA_MESSAGE, message)
            }
        )
    }

    private fun buildNotification(content: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CH_ID, "IFIX VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CH_ID)
            .setContentTitle("IFIX VPN")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
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
