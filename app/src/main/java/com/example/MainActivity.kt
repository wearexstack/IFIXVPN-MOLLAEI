package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LicenseActivationScreen
import com.example.ui.screens.ServerListScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.SubscriptionScreen
import com.example.ui.screens.UpdateDialog
import com.example.ui.theme.IfixVpnTheme
import com.example.viewmodel.VpnViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: VpnViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            val activeLicense by viewModel.activeLicense.collectAsState()
            val connectionStatus by viewModel.connectionStatus.collectAsState()
            val selectedServer by viewModel.selectedServer.collectAsState()
            val filteredServers by viewModel.filteredServers.collectAsState()
            val allSubscriptions by viewModel.allSubscriptions.collectAsState()
            val vpnStats by viewModel.vpnStats.collectAsState()
            val remoteConfig by viewModel.remoteConfig.collectAsState()
            val searchQuery by viewModel.searchQuery.collectAsState()
            val selectedProtocolFilter by viewModel.selectedProtocolFilter.collectAsState()
            val licenseError by viewModel.licenseError.collectAsState()
            val licenseSuccessMsg by viewModel.licenseSuccessMsg.collectAsState()
            val isUpdateDialogVisible by viewModel.isUpdateDialogVisible.collectAsState()
            val isRefreshingSub by viewModel.isRefreshingSub.collectAsState()

            IfixVpnTheme(darkTheme = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "splash") {
                        composable("splash") {
                            SplashScreen(
                                isLicenseActive = activeLicense?.status == "ACTIVE",
                                onSplashFinished = { targetDestination ->
                                    navController.navigate(targetDestination) {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("license") {
                            LicenseActivationScreen(
                                licenseError = licenseError,
                                licenseSuccessMsg = licenseSuccessMsg,
                                onActivateKey = { key -> viewModel.activateLicense(key) },
                                onNavigateHome = {
                                    navController.navigate("home") {
                                        popUpTo("license") { inclusive = true }
                                    }
                                },
                                onClearMessages = { viewModel.clearMessages() }
                            )
                        }

                        composable("home") {
                            HomeScreen(
                                connectionStatus = connectionStatus,
                                selectedServer = selectedServer,
                                vpnStats = vpnStats,
                                activeLicense = activeLicense,
                                remoteConfig = remoteConfig,
                                isDarkMode = isDarkMode,
                                onToggleConnect = { viewModel.toggleConnect() },
                                onNavigateServerList = { navController.navigate("servers") },
                                onNavigateSubscriptions = { navController.navigate("subscriptions") },
                                onNavigateSettings = { navController.navigate("settings") },
                                onToggleTheme = { viewModel.toggleTheme() }
                            )
                        }

                        composable("servers") {
                            ServerListScreen(
                                serverList = filteredServers,
                                selectedServer = selectedServer,
                                searchQuery = searchQuery,
                                selectedProtocolFilter = selectedProtocolFilter,
                                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                onProtocolFilterSelect = { viewModel.setProtocolFilter(it) },
                                onSelectServer = { viewModel.selectServer(it) },
                                onTestAllLatencies = { viewModel.testAllLatencies() },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("subscriptions") {
                            SubscriptionScreen(
                                subscriptions = allSubscriptions,
                                isRefreshing = isRefreshingSub,
                                statusMessage = licenseSuccessMsg ?: licenseError,
                                onAddSubscription = { url -> viewModel.addSubscription(url) },
                                onDeleteSubscription = { id -> viewModel.deleteSubscription(id) },
                                onRefreshSubscription = { viewModel.refreshSubscription() },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                activeLicense = activeLicense,
                                remoteConfig = remoteConfig,
                                isDarkMode = isDarkMode,
                                onToggleTheme = { viewModel.toggleTheme() },
                                onDeactivateLicense = {
                                    viewModel.deactivateLicense()
                                    navController.navigate("license") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                },
                                onCheckForUpdates = { viewModel.setUpdateDialogVisible(true) },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }

                    if (isUpdateDialogVisible) {
                        UpdateDialog(
                            remoteConfig = remoteConfig,
                            onDismiss = { viewModel.setUpdateDialogVisible(false) },
                            onDownloadUpdate = {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Downloading IFIX VPN v${remoteConfig.latestVersionName}...",
                                    Toast.LENGTH_LONG
                                ).show()
                                viewModel.setUpdateDialogVisible(false)
                            }
                        )
                    }
                }
            }
        }
    }
}
