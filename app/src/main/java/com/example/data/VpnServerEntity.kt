package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vpn_servers")
data class VpnServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val countryCode: String,
    val countryName: String,
    val ipOrDomain: String,
    val port: Int,
    val protocol: String, // VLESS, VMess, Trojan, Shadowsocks, Xray, V2Ray
    val latencyMs: Int,
    val status: String = "ONLINE", // ONLINE, BUSY, MAINTENANCE
    val userCapacityPercent: Int = 45,
    val flagEmoji: String,
    val configRawUrl: String = "",
    val isFavorite: Boolean = false
)
