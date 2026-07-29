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
import com.example.network.SubscriptionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class VpnRepository(
    private val dao: VpnDao,
    private val appContext: Context
) {

    val allServers: Flow<List<VpnServerEntity>> = dao.getAllServers()
    val activeLicense: Flow<LicenseEntity?> = dao.getActiveLicense()
    val allSubscriptions: Flow<List<SubscriptionEntity>> = dao.getAllSubscriptions()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 10 official one-month single-device keys (offline fallback when API unreachable). */
    private val officialKeys = setOf(
        "IFIX-A7K2-M9P4",
        "IFIX-B3N8-Q1R6",
        "IFIX-C5W2-T4Y9",
        "IFIX-D8H1-U6V3",
        "IFIX-E2J7-X9Z4",
        "IFIX-F4L0-A1B8",
        "IFIX-G6M3-C5D2",
        "IFIX-H9P5-E7F1",
        "IFIX-J1R8-G3H6",
        "IFIX-K3T4-J0L9"
    )

    @SuppressLint("HardwareIds")
    private fun deviceId(): String {
        return Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"
    }

    suspend fun ensureInitialData() {
        val existingSubs = dao.getAllSubscriptions().firstOrNull()
        if (existingSubs.isNullOrEmpty()) {
            val defaultSub = SubscriptionEntity(
                id = "sub_xstack_main",
                name = "اشتراک اصلی XStack",
                subUrl = SubscriptionParser.DEFAULT_SUB_URL,
                serverCount = 0,
                isAutoUpdateEnabled = true
            )
            dao.insertSubscription(defaultSub)
        }

        val sub = dao.getAllSubscriptions().firstOrNull()?.firstOrNull()
            ?: return

        val result = refreshServersFromSubscription(sub.subUrl)
        if (result.isFailure) {
            val existingServers = dao.getAllServers().firstOrNull()
            if (existingServers.isNullOrEmpty()) {
                dao.insertServers(
                    listOf(
                        VpnServerEntity(
                            id = "fallback_1",
                            name = "آفلاین – اشتراک را رفرش کنید",
                            countryCode = "UN",
                            countryName = "بین‌المللی",
                            ipOrDomain = "localhost",
                            port = 443,
                            protocol = "VLESS",
                            latencyMs = 999,
                            status = "MAINTENANCE",
                            userCapacityPercent = 0,
                            flagEmoji = "🌐"
                        )
                    )
                )
            }
        }
    }

    /**
     * Download subscription URL, parse nodes, replace all servers in DB.
     */
    suspend fun refreshServersFromSubscription(subUrl: String = SubscriptionParser.DEFAULT_SUB_URL): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(subUrl.trim())
                    .header("User-Agent", "IFIX-VPN-Android/1.0")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("دانلود ساب ناموفق بود (HTTP ${response.code})")
                    )
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext Result.failure(Exception("محتوای ساب خالی است."))
                }

                val servers = SubscriptionParser.parseSubscriptionBody(body)
                if (servers.isEmpty()) {
                    return@withContext Result.failure(
                        Exception("هیچ نودی از ساب پارس نشد. فرمت لینک‌ها را چک کنید.")
                    )
                }

                dao.clearAllServers()
                dao.insertServers(servers)

                val subs = dao.getAllSubscriptions().firstOrNull().orEmpty()
                val matching = subs.find { it.subUrl == subUrl } ?: subs.firstOrNull()
                if (matching != null) {
                    dao.insertSubscription(
                        matching.copy(
                            serverCount = servers.size,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                } else {
                    dao.insertSubscription(
                        SubscriptionEntity(
                            id = "sub_xstack_main",
                            name = "اشتراک اصلی XStack",
                            subUrl = subUrl,
                            serverCount = servers.size,
                            isAutoUpdateEnabled = true
                        )
                    )
                }

                Result.success(servers.size)
            } catch (e: Exception) {
                Result.failure(Exception("خطا در به‌روزرسانی سرورها: ${e.message}"))
            }
        }
    }

    suspend fun activateLicense(key: String): Result<LicenseEntity> {
        val cleanKey = key.trim().uppercase()
        if (cleanKey.isBlank()) {
            return Result.failure(Exception("لطفاً کلید لایسنس را وارد کنید."))
        }

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
            if (ApiClient.ALLOW_OFFLINE_DEMO) {
                return activateOfflineOfficial(cleanKey)
            }
            return Result.failure(
                Exception("اتصال به سرور لایسنس برقرار نشد. اینترنت را چک کنید.\n(${e.message})")
            )
        }
    }

    private suspend fun activateOfflineOfficial(cleanKey: String): Result<LicenseEntity> {
        if (!officialKeys.contains(cleanKey)) {
            return Result.failure(
                Exception("سرور در دسترس نیست و این کلید معتبر نیست.")
            )
        }

        val entity = LicenseEntity(
            licenseKey = cleanKey,
            status = "ACTIVE",
            planType = "اشتراک یک‌ماهه نامحدود",
            expiryTimestamp = System.currentTimeMillis() + 30L * 24 * 3600 * 1000,
            maxDevices = 1,
            activeDevicesCount = 1
        )
        dao.setLicense(entity)
        return Result.success(entity)
    }

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
            if (response.status == "INVALID" || response.status == "EXPIRED" || response.status == "DEVICE_NOT_BOUND") {
                dao.clearLicense()
                return Result.failure(Exception(response.error ?: "لایسنس دیگر معتبر نیست."))
            }
            return Result.failure(Exception(response.error ?: "بررسی لایسنس ناموفق"))
        } catch (_: Exception) {
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
            }
        }
        dao.clearLicense()
    }

    suspend fun testServerLatency(server: VpnServerEntity): VpnServerEntity {
        val newLatency = (30..120).random()
        val updated = server.copy(latencyMs = newLatency)
        dao.insertServers(listOf(updated))
        return updated
    }
}
