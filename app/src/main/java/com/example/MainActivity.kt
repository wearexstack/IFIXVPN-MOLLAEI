package com.example

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
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

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
    }

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
            val vpnPermissionIntent by viewModel.vpnPermissionIntent.collectAsState()

            LaunchedEffect(vpnPermissionIntent) {
                vpnPermissionIntent?.let { vpnPermissionLauncher.launch(it) }
            }

            // Surface connection / license errors as Toast on every screen
            LaunchedEffect(licenseError) {
                licenseError?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                    viewModel.clearMessages()
                }
            }
            LaunchedEffect(licenseSuccessMsg) {
                licenseSuccessMsg?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                    viewModel.clearMessages()
                }
            }

            IfixVpnTheme(darkTheme = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "splash") {
                        composable("splash") {
                            SplashScreen(
                                isLicenseActive = activeLicense?.status == "ACTIVE",
                                onSplashFinished = { target ->
                                    navController.navigate(target) {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("license") {
                            LicenseActivationScreen(
                                licenseError = null,
                                licenseSuccessMsg = null,
                                onActivateKey = { viewModel.activateLicense(it) },
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
                                onNavigateSubscriptions = {
                                    viewModel.refreshSubscription()
                                    navController.navigate("subscriptions")
                                },
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
                                statusMessage = null,
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
                                    "در حال دانلود نسخه ${remoteConfig.latestVersionName}...",
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
