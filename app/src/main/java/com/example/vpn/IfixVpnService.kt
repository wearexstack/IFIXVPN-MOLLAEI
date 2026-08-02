package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

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
            context.startService(
                Intent(context, IfixVpnService::class.java).apply { action = ACTION_DISCONNECT }
            )
        }
    }

    private var xray: XrayEngine? = null
    private val starting = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        // Warm up native env early
        XrayEngine.ensureEnv(applicationContext)
    }

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
                Thread({ startTunnel(configUri, serverName) }, "ifix-xray-start").start()
            }
            else -> stopTunnel(null)
        }
        return START_STICKY
    }

    private fun startTunnel(configUri: String, serverName: String) {
        if (!starting.compareAndSet(false, true)) return
        try {
            broadcast("connecting", null)

            val lower = configUri.lowercase()
            if (lower.startsWith("hysteria2://") || lower.startsWith("hy2://")) {
                broadcast(
                    "error",
                    "Hysteria2 با Xray پشتیبانی نمی‌شود. سرور VLESS/Trojan/VMess انتخاب کنید."
                )
                stopSelf()
                return
            }

            if (!XrayEngine.isAvailable()) {
                broadcast(
                    "error",
                    "کتابخانه Xray (libv2ray.aar) در این APK نیست. از بیلد Actions جدید استفاده کنید."
                )
                stopSelf()
                return
            }

            val xrayJson = try {
                XrayConfigBuilder.build(configUri)
            } catch (e: Exception) {
                broadcast("error", "کانفیگ نامعتبر: ${e.message}")
                stopSelf()
                return
            }

            File(filesDir, "vpn").mkdirs()
            File(filesDir, "vpn/xray.json").writeText(xrayJson)
            Log.i(TAG, "config ready ${xrayJson.length} bytes")

            xray?.stop()
            xray = XrayEngine(this)
            val ok = xray!!.start(applicationContext, xrayJson, "IFIX · $serverName")
            if (!ok) {
                val detail = xray?.lastError ?: "نامشخص"
                Log.e(TAG, "core start failed: $detail")
                broadcast("error", "هسته Xray: $detail")
                stopSelf()
                return
            }

            isRunning = true
            startForeground(NOTIF_ID, buildNotification("متصل · $serverName"))
            broadcast("connected", serverName)
        } catch (e: Exception) {
            Log.e(TAG, "startTunnel", e)
            broadcast("error", e.message ?: "خطا در اتصال")
            stopTunnel(null)
        } finally {
            starting.set(false)
        }
    }

    private fun stopTunnel(message: String?) {
        try {
            xray?.stop()
        } catch (_: Exception) {
        }
        xray = null
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
