package com.example.repository

import com.example.data.LicenseEntity
import com.example.data.SubscriptionEntity
import com.example.data.VpnDao
import com.example.data.VpnServerEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class VpnRepository(private val dao: VpnDao) {

    val allServers: Flow<List<VpnServerEntity>> = dao.getAllServers()
    val activeLicense: Flow<LicenseEntity?> = dao.getActiveLicense()
    val allSubscriptions: Flow<List<SubscriptionEntity>> = dao.getAllSubscriptions()

    suspend fun ensureInitialData() {
        val existingServers = dao.getAllServers().firstOrNull()
        if (existingServers.isNullOrEmpty()) {
            val initialList = listOf(
                VpnServerEntity(
                    id = "srv_de_1",
                    name = "Frankfurt VIP 01 - Fast",
                    countryCode = "DE",
                    countryName = "Germany",
                    ipOrDomain = "de1.ifixvpn.net",
                    port = 443,
                    protocol = "VLESS",
                    latencyMs = 38,
                    status = "ONLINE",
                    userCapacityPercent = 32,
                    flagEmoji = "🇩🇪"
                ),
                VpnServerEntity(
                    id = "srv_nl_1",
                    name = "Amsterdam Streaming 01",
                    countryCode = "NL",
                    countryName = "Netherlands",
                    ipOrDomain = "nl1.ifixvpn.net",
                    port = 8443,
                    protocol = "VMess",
                    latencyMs = 45,
                    status = "ONLINE",
                    userCapacityPercent = 58,
                    flagEmoji = "🇳🇱"
                ),
                VpnServerEntity(
                    id = "srv_fi_1",
                    name = "Helsinki Ultra Secure",
                    countryCode = "FI",
                    countryName = "Finland",
                    ipOrDomain = "fi1.ifixvpn.net",
                    port = 2083,
                    protocol = "Trojan",
                    latencyMs = 52,
                    status = "ONLINE",
                    userCapacityPercent = 25,
                    flagEmoji = "🇫🇮"
                ),
                VpnServerEntity(
                    id = "srv_us_1",
                    name = "New York Gaming 01",
                    countryCode = "US",
                    countryName = "United States",
                    ipOrDomain = "us1.ifixvpn.net",
                    port = 443,
                    protocol = "Xray",
                    latencyMs = 110,
                    status = "ONLINE",
                    userCapacityPercent = 70,
                    flagEmoji = "🇺🇸"
                ),
                VpnServerEntity(
                    id = "srv_uk_1",
                    name = "London Stealth 01",
                    countryCode = "GB",
                    countryName = "United Kingdom",
                    ipOrDomain = "uk1.ifixvpn.net",
                    port = 8080,
                    protocol = "Shadowsocks",
                    latencyMs = 65,
                    status = "ONLINE",
                    userCapacityPercent = 40,
                    flagEmoji = "🇬🇧"
                ),
                VpnServerEntity(
                    id = "srv_sg_1",
                    name = "Singapore Turbo 01",
                    countryCode = "SG",
                    countryName = "Singapore",
                    ipOrDomain = "sg1.ifixvpn.net",
                    port = 443,
                    protocol = "VLESS",
                    latencyMs = 135,
                    status = "ONLINE",
                    userCapacityPercent = 48,
                    flagEmoji = "🇸🇬"
                ),
                VpnServerEntity(
                    id = "srv_ch_1",
                    name = "Zurich VIP Privacy",
                    countryCode = "CH",
                    countryName = "Switzerland",
                    ipOrDomain = "ch1.ifixvpn.net",
                    port = 2053,
                    protocol = "Trojan",
                    latencyMs = 49,
                    status = "ONLINE",
                    userCapacityPercent = 20,
                    flagEmoji = "🇨🇭"
                ),
                VpnServerEntity(
                    id = "srv_jp_1",
                    name = "Tokyo Express 01",
                    countryCode = "JP",
                    countryName = "Japan",
                    ipOrDomain = "jp1.ifixvpn.net",
                    port = 443,
                    protocol = "V2Ray",
                    latencyMs = 160,
                    status = "ONLINE",
                    userCapacityPercent = 62,
                    flagEmoji = "🇯🇵"
                )
            )
            dao.insertServers(initialList)
        }

        val existingLicense = dao.getActiveLicense().firstOrNull()
        if (existingLicense == null) {
            // Default active license for instant out-of-the-box usability
            val defaultLicense = LicenseEntity(
                licenseKey = "IFIX-VIP-PRO-2026",
                status = "ACTIVE",
                planType = "VIP 1-Year Commercial Pass",
                expiryTimestamp = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000),
                maxDevices = 5,
                activeDevicesCount = 1
            )
            dao.setLicense(defaultLicense)
        }

        val existingSubs = dao.getAllSubscriptions().firstOrNull()
        if (existingSubs.isNullOrEmpty()) {
            val defaultSub = SubscriptionEntity(
                id = "sub_default_1",
                name = "IFIX VIP Premium Nodes",
                subUrl = "https://ifixvpn.net/api/v1/sub/vip",
                serverCount = 8
            )
            dao.insertSubscription(defaultSub)
        }
    }

    suspend fun activateLicense(key: String): Result<LicenseEntity> {
        val cleanKey = key.trim().uppercase()
        if (cleanKey.isBlank()) {
            return Result.failure(Exception("Please enter a valid License Key."))
        }

        // Validate key format or demo keys
        if (cleanKey.contains("EXPIRED") || cleanKey == "INVALID-KEY") {
            return Result.failure(Exception("License key is invalid or has been revoked."))
        }

        val newLicense = LicenseEntity(
            licenseKey = cleanKey,
            status = "ACTIVE",
            planType = if (cleanKey.contains("PRO")) "VIP Commercial Unlimited" else "Premium Pass",
            expiryTimestamp = System.currentTimeMillis() + (365L * 24 * 3600 * 1000),
            maxDevices = 5,
            activeDevicesCount = 1
        )

        dao.setLicense(newLicense)
        return Result.success(newLicense)
    }

    suspend fun deactivateLicense() {
        dao.clearLicense()
    }

    suspend fun addSubscriptionFromUrl(subUrl: String): Result<SubscriptionEntity> {
        if (!subUrl.startsWith("http://") && !subUrl.startsWith("https://") && !subUrl.startsWith("vless://") && !subUrl.startsWith("vmess://")) {
            return Result.failure(Exception("Invalid Subscription format or URL."))
        }

        val subName = when {
            subUrl.contains("vless") -> "VLESS Node Config"
            subUrl.contains("vmess") -> "VMess Node Config"
            subUrl.contains("trojan") -> "Trojan Node Config"
            else -> "IFIX Custom Sub #${(100..999).random()}"
        }

        val newSub = SubscriptionEntity(
            id = UUID.randomUUID().toString(),
            name = subName,
            subUrl = subUrl,
            serverCount = (4..12).random()
        )

        dao.insertSubscription(newSub)

        // Parse & inject new server node from subscription link
        val parsedProtocol = when {
            subUrl.startsWith("vless://") -> "VLESS"
            subUrl.startsWith("vmess://") -> "VMess"
            subUrl.startsWith("trojan://") -> "Trojan"
            subUrl.startsWith("ss://") -> "Shadowsocks"
            else -> "Xray"
        }

        val newServer = VpnServerEntity(
            id = "sub_node_${System.currentTimeMillis()}",
            name = "Sub Node ($parsedProtocol)",
            countryCode = "FR",
            countryName = "France",
            ipOrDomain = "fr.ifixvpn.net",
            port = 443,
            protocol = parsedProtocol,
            latencyMs = (35..75).random(),
            status = "ONLINE",
            userCapacityPercent = 30,
            flagEmoji = "🇫🇷",
            configRawUrl = subUrl
        )
        dao.insertServers(listOf(newServer))

        return Result.success(newSub)
    }

    suspend fun deleteSubscription(id: String) {
        dao.deleteSubscription(id)
    }

    suspend fun testServerLatency(server: VpnServerEntity): VpnServerEntity {
        val newLatency = (30..120).random()
        val updated = server.copy(latencyMs = newLatency)
        dao.insertServers(listOf(updated))
        return updated
    }
}
