package com.example.viewmodel

import android.app.Application
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

    private val _remoteConfig = MutableStateFlow(RemoteConfig())
    val remoteConfig: StateFlow<RemoteConfig> = _remoteConfig.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedProtocolFilter = MutableStateFlow("All")
    val selectedProtocolFilter: StateFlow<String> = _selectedProtocolFilter.asStateFlow()

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isUpdateDialogVisible = MutableStateFlow(false)
    val isUpdateDialogVisible: StateFlow<Boolean> = _isUpdateDialogVisible.asStateFlow()

    private val _isDemoKeySheetOpen = MutableStateFlow(false)
    val isDemoKeySheetOpen: StateFlow<Boolean> = _isDemoKeySheetOpen.asStateFlow()

    private val _licenseError = MutableStateFlow<String?>(null)
    val licenseError: StateFlow<String?> = _licenseError.asStateFlow()

    private val _licenseSuccessMsg = MutableStateFlow<String?>(null)
    val licenseSuccessMsg: StateFlow<String?> = _licenseSuccessMsg.asStateFlow()

    private val _isActivatingLicense = MutableStateFlow(false)
    val isActivatingLicense: StateFlow<Boolean> = _isActivatingLicense.asStateFlow()

    private val _isRefreshingSub = MutableStateFlow(false)
    val isRefreshingSub: StateFlow<Boolean> = _isRefreshingSub.asStateFlow()

    val activeLicense: StateFlow<LicenseEntity?> = repository.activeLicense
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allSubscriptions: StateFlow<List<SubscriptionEntity>> = repository.allSubscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredServers: StateFlow<List<VpnServerEntity>> = combine(
        repository.allServers,
        _searchQuery,
        _selectedProtocolFilter
    ) { serverList, query, protocol ->
        var list = serverList
        if (_selectedServer.value == null && list.isNotEmpty()) {
            _selectedServer.value = list.first()
        }
        // If selected server disappeared after refresh, pick first
        val selectedId = _selectedServer.value?.id
        if (selectedId != null && list.none { it.id == selectedId } && list.isNotEmpty()) {
            _selectedServer.value = list.first()
        }
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

    /** Interval for automatic subscription refresh while app is in foreground (30 minutes). */
    private companion object {
        const val AUTO_REFRESH_INTERVAL_MS = 30 * 60 * 1000L
    }

    init {
        viewModelScope.launch {
            repository.ensureInitialData()
            repository.revalidateLicense()
        }
        startAutoRefresh()
    }

    /**
     * Periodically re-downloads the subscription and updates the server list.
     * Runs only while the ViewModel (app process) is alive.
     * Edit https://raw.githubusercontent.com/wearexstack/xstack/main/sub → servers update on next cycle.
     */
    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            // Wait a bit after startup so initial load finishes first
            delay(60_000L)
            while (isActive) {
                try {
                    val target = allSubscriptions.value.firstOrNull()?.subUrl
                        ?: SubscriptionParser.DEFAULT_SUB_URL
                    repository.refreshServersFromSubscription(target)
                } catch (_: Exception) {
                    // Silent fail – next cycle will retry
                }
                delay(AUTO_REFRESH_INTERVAL_MS)
            }
        }
    }

    fun toggleConnect() {
        val current = _connectionStatus.value
        if (current == ConnectionStatus.DISCONNECTED) {
            _connectionStatus.value = ConnectionStatus.CONNECTING
            viewModelScope.launch {
                delay(1500)
                _connectionStatus.value = ConnectionStatus.CONNECTED
                startVpnTimerAndStats()
            }
        } else if (current == ConnectionStatus.CONNECTED) {
            _connectionStatus.value = ConnectionStatus.DISCONNECTING
            viewModelScope.launch {
                delay(800)
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                stopVpnTimerAndStats()
            }
        }
    }

    private fun startVpnTimerAndStats() {
        timerJob?.cancel()
        var duration = 0L
        var totalDown = 0L
        var totalUp = 0L

        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                duration++
                val downSpeed = (1200..8500).random().toDouble() / 10.0
                val upSpeed = (400..3200).random().toDouble() / 10.0
                totalDown += (downSpeed * 1024).toLong()
                totalUp += (upSpeed * 1024).toLong()
                val serverIp = _selectedServer.value?.ipOrDomain ?: "185.220.101.5"
                _vpnStats.value = VpnStats(
                    durationSeconds = duration,
                    downloadSpeedKbps = downSpeed,
                    uploadSpeedKbps = upSpeed,
                    totalDownloadBytes = totalDown,
                    totalUploadBytes = totalUp,
                    currentIp = serverIp
                )
            }
        }
    }

    private fun stopVpnTimerAndStats() {
        timerJob?.cancel()
        _vpnStats.value = VpnStats()
    }

    fun selectServer(server: VpnServerEntity) {
        _selectedServer.value = server
        if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
            viewModelScope.launch {
                _connectionStatus.value = ConnectionStatus.CONNECTING
                delay(1200)
                _connectionStatus.value = ConnectionStatus.CONNECTED
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
            result.onSuccess {
                _licenseSuccessMsg.value = "✅ لایسنس با موفقیت فعال شد!\n${it.planType}"
            }.onFailure {
                _licenseError.value = it.message ?: "فعال‌سازی ناموفق بود."
            }
        }
    }

    fun deactivateLicense() {
        viewModelScope.launch {
            repository.deactivateLicense()
        }
    }

    fun addSubscription(url: String) {
        viewModelScope.launch {
            val result = repository.addSubscriptionFromUrl(url)
            result.onSuccess {
                _licenseSuccessMsg.value = "اشتراک اضافه شد و ${it.serverCount} سرور بارگذاری شد."
            }.onFailure {
                _licenseError.value = it.message ?: "خطا در افزودن اشتراک"
            }
        }
    }

    /** Re-download the main sub (or given URL) and replace server list */
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

    fun deleteSubscription(id: String) {
        viewModelScope.launch {
            repository.deleteSubscription(id)
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
            filteredServers.value.forEach {
                repository.testServerLatency(it)
            }
        }
    }

    fun setUpdateDialogVisible(visible: Boolean) {
        _isUpdateDialogVisible.value = visible
    }

    fun setDemoKeySheetOpen(open: Boolean) {
        _isDemoKeySheetOpen.value = open
    }

    fun clearMessages() {
        _licenseError.value = null
        _licenseSuccessMsg.value = null
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
        timerJob?.cancel()
    }
}
