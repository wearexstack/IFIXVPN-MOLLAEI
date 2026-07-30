package com.example.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.LicenseEntity
import com.example.data.SubscriptionEntity
import com.example.data.VpnServerEntity
import com.example.models.ConnectionStatus
import com.example.models.RemoteConfig
import com.example.models.VpnStats
import com.example.network.SubscriptionParser
import com.example.repository.VpnRepository
import com.example.vpn.IfixVpnService
import com.example.vpn.XrayEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = VpnRepository(db.vpnDao(), application.applicationContext)

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _selectedServer = MutableStateFlow<VpnServerEntity?>(null)
    val selectedServer: StateFlow<VpnServerEntity?> = _selectedServer.asStateFlow()

    private val _vpnStats = MutableStateFlow(VpnStats())
    val vpnStats: StateFlow<VpnStats> = _vpnStats.asStateFlow()

    private val _remoteConfig = MutableStateFlow(
        RemoteConfig(
            announcementMessage = if (XrayEngine.isAvailable()) {
                "هسته Xray آماده است. سرور را انتخاب و وصل شوید."
            } else {
                "هشدار: libv2ray.aar در APK نیست – اتصال واقعی کار نمی‌کند تا AAR اضافه شود."
            }
        )
    )
    val remoteConfig: StateFlow<RemoteConfig> = _remoteConfig.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedProtocolFilter = MutableStateFlow("All")
    val selectedProtocolFilter: StateFlow<String> = _selectedProtocolFilter.asStateFlow()

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isUpdateDialogVisible = MutableStateFlow(false)
    val isUpdateDialogVisible: StateFlow<Boolean> = _isUpdateDialogVisible.asStateFlow()

    private val _licenseError = MutableStateFlow<String?>(null)
    val licenseError: StateFlow<String?> = _licenseError.asStateFlow()

    private val _licenseSuccessMsg = MutableStateFlow<String?>(null)
    val licenseSuccessMsg: StateFlow<String?> = _licenseSuccessMsg.asStateFlow()

    private val _isActivatingLicense = MutableStateFlow(false)
    val isActivatingLicense: StateFlow<Boolean> = _isActivatingLicense.asStateFlow()

    /** True after first Room + revalidate pass — splash waits for this. */
    private val _licenseReady = MutableStateFlow(false)
    val licenseReady: StateFlow<Boolean> = _licenseReady.asStateFlow()

    private val _isRefreshingSub = MutableStateFlow(false)
    val isRefreshingSub: StateFlow<Boolean> = _isRefreshingSub.asStateFlow()

    private val _vpnPermissionIntent = MutableStateFlow<Intent?>(null)
    val vpnPermissionIntent: StateFlow<Intent?> = _vpnPermissionIntent.asStateFlow()

    val activeLicense: StateFlow<LicenseEntity?> = repository.activeLicense
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val allSubscriptions: StateFlow<List<SubscriptionEntity>> = repository.allSubscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredServers: StateFlow<List<VpnServerEntity>> = combine(
        repository.allServers,
        _searchQuery,
        _selectedProtocolFilter
    ) { serverList, query, protocol ->
        if (_selectedServer.value == null && serverList.isNotEmpty()) {
            _selectedServer.value = serverList.firstOrNull {
                it.configRawUrl.isNotBlank() && it.status != "MAINTENANCE"
            } ?: serverList.first()
        }
        val selectedId = _selectedServer.value?.id
        if (selectedId != null && serverList.none { it.id == selectedId } && serverList.isNotEmpty()) {
            _selectedServer.value = serverList.first()
        }
        var list = serverList
        if (query.isNotBlank()) {
            list = list.filter {
                it.countryName.contains(query, ignoreCase = true) ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.protocol.contains(query, ignoreCase = true)
            }
        }
        if (protocol != "All") {
            list = list.filter { it.protocol.equals(protocol, ignoreCase = true) }
        }
        list.sortedBy { it.latencyMs }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var timerJob: Job? = null
    private var autoRefreshJob: Job? = null

    private val vpnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != IfixVpnService.BROADCAST_STATE) return
            when (intent.getStringExtra(IfixVpnService.EXTRA_STATE)) {
                "connecting" -> _connectionStatus.value = ConnectionStatus.CONNECTING
                "connected" -> {
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    startVpnTimerAndStats()
                }
                "disconnected" -> {
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    stopVpnTimerAndStats()
                }
                "error" -> {
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    stopVpnTimerAndStats()
                    _licenseError.value = intent.getStringExtra(IfixVpnService.EXTRA_MESSAGE)
                        ?: "خطا در اتصال VPN"
                }
            }
        }
    }

    private companion object {
        const val AUTO_REFRESH_INTERVAL_MS = 30 * 60 * 1000L
    }

    init {
        val app = getApplication<Application>()
        val filter = IntentFilter(IfixVpnService.BROADCAST_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(vpnReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(vpnReceiver, filter)
        }

        viewModelScope.launch {
            try {
                repository.ensureInitialData()
                // Soft revalidate: keep local ACTIVE license if offline / API down
                repository.revalidateLicense()
            } finally {
                _licenseReady.value = true
            }
        }
        startAutoRefresh()

        if (IfixVpnService.isRunning) {
            _connectionStatus.value = ConnectionStatus.CONNECTED
            startVpnTimerAndStats()
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            delay(60_000L)
            while (isActive) {
                try {
                    val target = allSubscriptions.value.firstOrNull()?.subUrl
                        ?: SubscriptionParser.DEFAULT_SUB_URL
                    repository.refreshServersFromSubscription(target)
                } catch (_: Exception) {
                }
                delay(AUTO_REFRESH_INTERVAL_MS)
            }
        }
    }

    fun toggleConnect() {
        val app = getApplication<Application>()
        val current = _connectionStatus.value

        if (current == ConnectionStatus.CONNECTED || current == ConnectionStatus.CONNECTING) {
            _connectionStatus.value = ConnectionStatus.DISCONNECTING
            IfixVpnService.disconnect(app)
            return
        }

        if (current == ConnectionStatus.DISCONNECTING) return

        val server = _selectedServer.value
        if (server == null) {
            _licenseError.value = "ابتدا یک سرور انتخاب کنید."
            return
        }
        if (server.configRawUrl.isBlank()) {
            _licenseError.value = "این سرور لینک کانفیگ ندارد. ساب را رفرش کنید."
            return
        }
        if (server.status == "MAINTENANCE") {
            _licenseError.value = "این سرور در دسترس نیست. سرور دیگری انتخاب کنید."
            return
        }

        val prepare = VpnService.prepare(app)
        if (prepare != null) {
            _vpnPermissionIntent.value = prepare
            return
        }

        startVpnService(server)
    }

    fun onVpnPermissionResult(granted: Boolean) {
        _vpnPermissionIntent.value = null
        if (!granted) {
            _licenseError.value = "مجوز VPN داده نشد."
            return
        }
        val server = _selectedServer.value ?: return
        startVpnService(server)
    }

    private fun startVpnService(server: VpnServerEntity) {
        val app = getApplication<Application>()
        _connectionStatus.value = ConnectionStatus.CONNECTING
        _licenseError.value = null
        IfixVpnService.connect(app, server.configRawUrl, server.name)
    }

    private fun startVpnTimerAndStats() {
        timerJob?.cancel()
        var duration = 0L
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                duration++
                _vpnStats.value = VpnStats(
                    durationSeconds = duration,
                    downloadSpeedKbps = 0.0,
                    uploadSpeedKbps = 0.0,
                    totalDownloadBytes = 0,
                    totalUploadBytes = 0,
                    currentIp = _selectedServer.value?.ipOrDomain ?: "—"
                )
            }
        }
    }

    private fun stopVpnTimerAndStats() {
        timerJob?.cancel()
        _vpnStats.value = VpnStats()
    }

    fun selectServer(server: VpnServerEntity) {
        val wasConnected = _connectionStatus.value == ConnectionStatus.CONNECTED
        _selectedServer.value = server
        if (wasConnected) {
            val app = getApplication<Application>()
            IfixVpnService.disconnect(app)
            viewModelScope.launch {
                delay(600)
                if (VpnService.prepare(app) == null) {
                    startVpnService(server)
                } else {
                    _vpnPermissionIntent.value = VpnService.prepare(app)
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setProtocolFilter(protocol: String) {
        _selectedProtocolFilter.value = protocol
    }

    fun toggleTheme() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun activateLicense(key: String) {
        _licenseError.value = null
        _licenseSuccessMsg.value = null
        _isActivatingLicense.value = true
        viewModelScope.launch {
            val result = repository.activateLicense(key)
            _isActivatingLicense.value = false
            result.onSuccess { entity ->
                _licenseSuccessMsg.value =
                    "✅ لایسنس فعال شد\n${entity.planType}\nورود خودکار…"
                // activeLicense Flow from Room will also flip to ACTIVE → MainActivity navigates
            }.onFailure {
                _licenseError.value = it.message ?: "فعال‌سازی ناموفق بود."
            }
        }
    }

    fun deactivateLicense() {
        viewModelScope.launch { repository.deactivateLicense() }
    }

    fun refreshSubscription(url: String? = null) {
        _isRefreshingSub.value = true
        _licenseError.value = null
        viewModelScope.launch {
            val target = url
                ?: allSubscriptions.value.firstOrNull()?.subUrl
                ?: SubscriptionParser.DEFAULT_SUB_URL
            val result = repository.refreshServersFromSubscription(target)
            _isRefreshingSub.value = false
            result.onSuccess { count ->
                _licenseSuccessMsg.value = "✅ $count سرور از ساب به‌روز شد."
            }.onFailure {
                _licenseError.value = it.message ?: "به‌روزرسانی ساب ناموفق بود."
            }
        }
    }

    fun testServerLatency(server: VpnServerEntity) {
        viewModelScope.launch {
            val updated = repository.testServerLatency(server)
            if (_selectedServer.value?.id == server.id) {
                _selectedServer.value = updated
            }
        }
    }

    fun testAllLatencies() {
        viewModelScope.launch {
            filteredServers.value.forEach { repository.testServerLatency(it) }
        }
    }

    fun setUpdateDialogVisible(visible: Boolean) {
        _isUpdateDialogVisible.value = visible
    }

    fun clearMessages() {
        _licenseError.value = null
        _licenseSuccessMsg.value = null
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(vpnReceiver)
        } catch (_: Exception) {
        }
        autoRefreshJob?.cancel()
        timerJob?.cancel()
    }
}
