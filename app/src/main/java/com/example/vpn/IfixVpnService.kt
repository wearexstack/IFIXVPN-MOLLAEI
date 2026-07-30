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
 * IFIX system VPN service backed by sing-box **libbox** when available.
 *
 * Flow:
 * 1. Build sing-box JSON from share link
 * 2. Start LibboxEngine (CommandServer + PlatformInterface.openTun)
 * 3. Fallback: establish TUN only + log if libbox missing
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
            context.startService(
                Intent(context, IfixVpnService::class.java).apply { action = ACTION_DISCONNECT }
            )
        }
    }

    private var tunFd: ParcelFileDescriptor? = null
    private var engine: LibboxEngine? = null
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
            val dir = File(filesDir, "vpn").apply { mkdirs() }
            File(dir, "sing-box.json").writeText(singBoxJson)
            Log.i(TAG, "sing-box config ${singBoxJson.length} bytes written")

            engine?.stop()
            engine = LibboxEngine(this) { pfd ->
                tunFd = pfd
            }

            val libboxOk = if (LibboxEngine.isAvailable()) {
                engine!!.start(applicationContext, singBoxJson)
            } else {
                Log.w(TAG, "libbox.aar missing – place under app/libs/ and rebuild")
                false
            }

            if (!libboxOk) {
                // Fallback TUN so permission flow still works; traffic will NOT be fully proxied
                establishFallbackTun(serverName)
                broadcast(
                    "error",
                    "هسته libbox یافت نشد. فایل app/libs/libbox.aar را بسازید (scripts/build-libbox.sh)."
                )
                // Keep service alive with fallback TUN only if established
                if (tunFd == null) {
                    stopSelf()
                    return
                }
            }

            isRunning = true
            val note = if (libboxOk) "متصل · $serverName (sing-box)" else "تونل سیستم · بدون هسته"
            startForeground(NOTIF_ID, buildNotification(note))
            if (libboxOk) {
                broadcast("connected", serverName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startTunnel failed", e)
            broadcast("error", e.message ?: "خطا در اتصال")
            stopTunnel(null)
        } finally {
            starting.set(false)
        }
    }

    private fun establishFallbackTun(serverName: String) {
        try {
            tunFd?.close()
            val pfd = Builder()
                .setSession("IFIX · $serverName")
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .setBlocking(false)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setMetered(false)
                }
                .establish()
            tunFd = pfd
            Log.i(TAG, "fallback TUN fd=${pfd?.fd}")
        } catch (e: Exception) {
            Log.e(TAG, "fallback TUN failed", e)
        }
    }

    private fun stopTunnel(message: String?) {
        try {
            engine?.stop()
        } catch (_: Exception) {
        }
        engine = null
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
