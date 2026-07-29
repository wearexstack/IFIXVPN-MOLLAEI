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
import com.example.repository.VpnRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = VpnRepository(db.vpnDao())

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
    private var speedJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureInitialData()
        }
    }

    fun toggleConnect() {
        val current = _connectionStatus.value
        if (current == ConnectionStatus.DISCONNECTED) {
            _connectionStatus.value = ConnectionStatus.CONNECTING
            viewModelScope.launch {
                delay(1500) // Connection negotiation animation
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
        speedJob?.cancel()

        var duration = 0L
        var totalDown = 0L
        var totalUp = 0L

        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                duration++
                val downSpeed = (1200..8500).random().toDouble() / 10.0 // KB/s
                val upSpeed = (400..3200).random().toDouble() / 10.0 // KB/s
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
        speedJob?.cancel()
        _vpnStats.value = VpnStats()
    }

    fun selectServer(server: VpnServerEntity) {
        _selectedServer.value = server
        if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
            // Reconnect to new server seamlessly
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
        viewModelScope.launch {
            val result = repository.activateLicense(key)
            result.onSuccess {
                _licenseSuccessMsg.value = "License Activated Successfully! Commercial VIP Unlocked."
            }.onFailure {
                _licenseError.value = it.message ?: "Failed to activate license."
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
                _licenseSuccessMsg.value = "Subscription imported! New nodes updated."
            }.onFailure {
                _licenseError.value = it.message ?: "Failed to import subscription."
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
}
