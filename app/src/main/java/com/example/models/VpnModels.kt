package com.example.models

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

data class RemoteConfig(
    val announcementMessage: String = "⚡ Welcome to IFIX VPN! Ultra high-speed VIP servers are online with zero logging.",
    val isForceUpdateRequired: Boolean = false,
    val latestVersionName: String = "1.0.0",
    val latestVersionCode: Int = 1,
    val releaseNotes: String = "Initial Release of IFIX VPN with VLESS, VMess, Trojan, and Shadowsocks support.",
    val telegramChannel: String = "https://t.me/ifixvpn_official",
    val supportUrl: String = "https://ifixvpn.com/support"
)

data class VpnStats(
    val durationSeconds: Long = 0,
    val downloadSpeedKbps: Double = 0.0,
    val uploadSpeedKbps: Double = 0.0,
    val totalDownloadBytes: Long = 0,
    val totalUploadBytes: Long = 0,
    val currentIp: String = "185.220.101.5"
)
