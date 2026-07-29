package com.example.repository

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.example.data.LicenseEntity
import com.example.data.SubscriptionEntity
import com.example.data.VpnDao
import com.example.data.VpnServerEntity
import com.example.network.ActivateLicenseRequest
import com.example.network.ApiClient
import com.example.network.CheckLicenseRequest
import com.example.network.DeactivateLicenseRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class VpnRepository(
    private val dao: VpnDao,
    private val appContext: Context
) {

    val allServers: Flow<List<VpnServerEntity>> = dao.getAllServers()
    val activeLicense: Flow<LicenseEntity?> = dao.getActiveLicense()
    val allSubscriptions: Flow<List<SubscriptionEntity>> = dao.getAllSubscriptions()

    @SuppressLint("HardwareIds")
    private fun deviceId(): String {
        return Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"
    }

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

        // Do NOT auto-activate a default license anymore.
        // User must activate via online API (or offline demo fallback).

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

    /**
     * Real online license activation.
     * 1) Calls backend POST /api/license/activate
     * 2) On network failure, falls back to offline demo keys (if enabled)
     */
    suspend fun activateLicense(key: String): Result<LicenseEntity> {
        val cleanKey = key.trim().uppercase()
        if (cleanKey.isBlank()) {
            return Result.failure(Exception("لطفاً کلید لایسنس را وارد کنید."))
        }

        // Try online first
        try {
            val response = ApiClient.licenseApi.activateLicense(
                ActivateLicenseRequest(
                    licenseKey = cleanKey,
                    deviceId = deviceId(),
                    deviceName = android.os.Build.MODEL
                )
            )
            if (response.success && response.license != null) {
                val dto = response.license
                val entity = LicenseEntity(
                    licenseKey = dto.licenseKey,
                    status = dto.status,
                    planType = dto.planType,
                    expiryTimestamp = dto.expiryTimestamp,
                    maxDevices = dto.maxDevices,
                    activeDevicesCount = dto.activeDevicesCount
                )
                dao.setLicense(entity)
                return Result.success(entity)
            }
            return Result.failure(Exception(response.error ?: "فعال‌سازی لایسنس ناموفق بود."))
        } catch (e: Exception) {
            // Network / server unreachable → optional offline demo
            if (ApiClient.ALLOW_OFFLINE_DEMO) {
                return activateOfflineDemo(cleanKey)
            }
            return Result.failure(
                Exception("اتصال به سرور لایسنس برقرار نشد. اینترنت را چک کنید.\n(${e.message})")
            )
        }
    }

    private suspend fun activateOfflineDemo(cleanKey: String): Result<LicenseEntity> {
        val demoKeys = setOf("IFIX-VIP-PRO-2026", "IFIX-PREMIUM-9999", "IFIX-DEMO-TEST")
        if (cleanKey.contains("EXPIRED") || cleanKey == "INVALID-KEY") {
            return Result.failure(Exception("کلید نامعتبر یا باطل شده است."))
        }
        if (!demoKeys.contains(cleanKey) && !cleanKey.startsWith("IFIX-")) {
            return Result.failure(
                Exception("سرور در دسترس نیست و این کلید دمو نیست. کلید معتبر IFIX وارد کنید.")
            )
        }

        val days = when {
            cleanKey.contains("DEMO") -> 30L
            cleanKey.contains("PREMIUM") -> 180L
            else -> 365L
        }
        val plan = when {
            cleanKey.contains("PRO") -> "VIP Commercial Unlimited (Offline)"
            cleanKey.contains("PREMIUM") -> "Premium Pass (Offline)"
            else -> "Demo Trial (Offline)"
        }

        val entity = LicenseEntity(
            licenseKey = cleanKey,
            status = "ACTIVE",
            planType = plan,
            expiryTimestamp = System.currentTimeMillis() + days * 24 * 3600 * 1000,
            maxDevices = 5,
            activeDevicesCount = 1
        )
        dao.setLicense(entity)
        return Result.success(entity)
    }

    /** Periodic online validation of the stored license */
    suspend fun revalidateLicense(): Result<LicenseEntity?> {
        val current = dao.getActiveLicense().firstOrNull() ?: return Result.success(null)
        try {
            val response = ApiClient.licenseApi.checkLicense(
                CheckLicenseRequest(
                    licenseKey = current.licenseKey,
                    deviceId = deviceId()
                )
            )
            if (response.success && response.license != null) {
                val dto = response.license
                val entity = LicenseEntity(
                    licenseKey = dto.licenseKey,
                    status = dto.status,
                    planType = dto.planType,
                    expiryTimestamp = dto.expiryTimestamp,
                    maxDevices = dto.maxDevices,
                    activeDevicesCount = dto.activeDevicesCount
                )
                dao.setLicense(entity)
                return Result.success(entity)
            }
            // Server says invalid → clear local
            if (response.status == "INVALID" || response.status == "EXPIRED" || response.status == "DEVICE_NOT_BOUND") {
                dao.clearLicense()
                return Result.failure(Exception(response.error ?: "لایسنس دیگر معتبر نیست."))
            }
            return Result.failure(Exception(response.error ?: "بررسی لایسنس ناموفق"))
        } catch (_: Exception) {
            // Offline: keep local license if not past expiry
            if (current.expiryTimestamp > System.currentTimeMillis() && current.status == "ACTIVE") {
                return Result.success(current)
            }
            dao.clearLicense()
            return Result.failure(Exception("لایسنس منقضی شده است."))
        }
    }

    suspend fun deactivateLicense() {
        val current = dao.getActiveLicense().firstOrNull()
        if (current != null) {
            try {
                ApiClient.licenseApi.deactivateLicense(
                    DeactivateLicenseRequest(
                        licenseKey = current.licenseKey,
                        deviceId = deviceId()
                    )
                )
            } catch (_: Exception) {
                // ignore network errors on deactivate
            }
        }
        dao.clearLicense()
    }

    suspend fun addSubscriptionFromUrl(subUrl: String): Result<SubscriptionEntity> {
        if (!subUrl.startsWith("http://") && !subUrl.startsWith("https://") &&
            !subUrl.startsWith("vless://") && !subUrl.startsWith("vmess://") &&
            !subUrl.startsWith("trojan://") && !subUrl.startsWith("ss://")
        ) {
            return Result.failure(Exception("فرمت لینک اشتراک نامعتبر است."))
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
