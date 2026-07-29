package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LicenseEntity
import com.example.data.VpnServerEntity
import com.example.models.ConnectionStatus
import com.example.models.RemoteConfig
import com.example.models.VpnStats
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.VpnConnectedGreen
import com.example.ui.theme.VpnConnectingYellow
import com.example.ui.theme.VpnDisconnectedRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    connectionStatus: ConnectionStatus,
    selectedServer: VpnServerEntity?,
    vpnStats: VpnStats,
    activeLicense: LicenseEntity?,
    remoteConfig: RemoteConfig,
    isDarkMode: Boolean,
    onToggleConnect: () -> Unit,
    onNavigateServerList: () -> Unit,
    onNavigateSubscriptions: () -> Unit,
    onNavigateSettings: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val scrollState = rememberScrollState()

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (connectionStatus == ConnectionStatus.CONNECTING || connectionStatus == ConnectionStatus.CONNECTED) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val buttonColor by animateColorAsState(
        targetValue = when (connectionStatus) {
            ConnectionStatus.CONNECTED -> VpnConnectedGreen
            ConnectionStatus.CONNECTING -> VpnConnectingYellow
            ConnectionStatus.DISCONNECTING -> VpnConnectingYellow
            ConnectionStatus.DISCONNECTED -> VpnDisconnectedRed
        },
        animationSpec = tween(500),
        label = "button_color"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(CyanPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "IFIX VPN",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (activeLicense != null) {
                                Text(
                                    text = activeLicense.planType,
                                    fontSize = 10.sp,
                                    color = CyanPrimary
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateSubscriptions) {
                        Icon(
                            imageVector = Icons.Default.RssFeed,
                            contentDescription = "Subscriptions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateServerList) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = "Servers",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Theme",
                            tint = CyanPrimary
                        )
                    }
                    IconButton(onClick = onNavigateSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("home_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Announcement banner
            if (remoteConfig.announcementMessage.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = "Announcement",
                            tint = CyanPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = remoteConfig.announcementMessage,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Connection Status Label
            Text(
                text = when (connectionStatus) {
                    ConnectionStatus.CONNECTED -> "SECURELY CONNECTED"
                    ConnectionStatus.CONNECTING -> "ESTABLISHING SECURE TUNNEL..."
                    ConnectionStatus.DISCONNECTING -> "DISCONNECTING..."
                    ConnectionStatus.DISCONNECTED -> "NOT CONNECTED"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = buttonColor
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Main Power Action Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(200.dp)
                    .testTag("connect_vpn_button")
            ) {
                // Outer Pulsing Glow Circle
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(buttonColor.copy(alpha = 0.15f))
                        .border(2.dp, buttonColor.copy(alpha = 0.4f), CircleShape)
                )

                // Inner Main Action Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(145.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    buttonColor,
                                    buttonColor.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .clickable { onToggleConnect() }
                        .border(4.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Connect Toggle",
                            tint = Color.White,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = if (connectionStatus == ConnectionStatus.CONNECTED) "STOP" else "START",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Server Selector Bar Card
            Card(
                onClick = onNavigateServerList,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("select_server_tile")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = selectedServer?.flagEmoji ?: "🌐",
                            fontSize = 28.sp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = selectedServer?.name ?: "Select Server Location",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${selectedServer?.protocol ?: "VLESS"} • ${selectedServer?.latencyMs ?: 0}ms",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Select Server",
                        tint = CyanPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Live Connection Stats Dashboard Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Duration Tile
                StatTile(
                    icon = Icons.Default.Timer,
                    label = "DURATION",
                    value = formatDuration(vpnStats.durationSeconds),
                    accentColor = CyanPrimary,
                    modifier = Modifier.weight(1f)
                )
                // Ping Tile
                StatTile(
                    icon = Icons.Default.Speed,
                    label = "PING",
                    value = "${selectedServer?.latencyMs ?: 0} ms",
                    accentColor = VpnConnectedGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Download Speed Tile
                StatTile(
                    icon = Icons.Default.Download,
                    label = "DOWNLOAD",
                    value = String.format("%.1f KB/s", vpnStats.downloadSpeedKbps),
                    accentColor = CyanSecondary,
                    modifier = Modifier.weight(1f)
                )
                // Upload Speed Tile
                StatTile(
                    icon = Icons.Default.Upload,
                    label = "UPLOAD",
                    value = String.format("%.1f KB/s", vpnStats.uploadSpeedKbps),
                    accentColor = VpnConnectingYellow,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // IP Address Tile
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "IP",
                            tint = CyanPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "SECURED IP ADDRESS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = vpnStats.currentIp,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun StatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format("%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}
