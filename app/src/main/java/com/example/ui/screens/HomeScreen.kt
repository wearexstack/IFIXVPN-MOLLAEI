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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
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
import com.example.ui.theme.IfixAccent
import com.example.ui.theme.StatusOff
import com.example.ui.theme.StatusOn
import com.example.ui.theme.StatusWait

/**
 * IFIX home – original layout focused on one-tap connect + server pick + live stats.
 * Inspired only by general VPN UX patterns (connect button, latency, server row), not any specific app UI.
 */
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
    val scroll = rememberScrollState()
    val statusColor by animateColorAsState(
        when (connectionStatus) {
            ConnectionStatus.CONNECTED -> StatusOn
            ConnectionStatus.CONNECTING, ConnectionStatus.DISCONNECTING -> StatusWait
            ConnectionStatus.DISCONNECTED -> StatusOff
        },
        label = "status"
    )
    val pulse = rememberInfiniteTransition(label = "p")
    val scale by pulse.animateFloat(
        1f,
        if (connectionStatus == ConnectionStatus.CONNECTED || connectionStatus == ConnectionStatus.CONNECTING) 1.08f else 1f,
        infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "s"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(IfixAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Shield, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("IFIX VPN", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                activeLicense?.planType ?: "بدون لایسنس",
                                fontSize = 11.sp,
                                color = IfixAccent
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateSubscriptions) {
                        Icon(Icons.Default.Refresh, contentDescription = "به‌روزرسانی سرورها")
                    }
                    IconButton(onClick = onNavigateServerList) {
                        Icon(Icons.Default.Dns, contentDescription = "لیست سرور")
                    }
                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "تنظیمات")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("home_screen")
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 20.dp)
                .verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (remoteConfig.announcementMessage.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, null, tint = IfixAccent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(remoteConfig.announcementMessage, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                when (connectionStatus) {
                    ConnectionStatus.CONNECTED -> "محافظت فعال"
                    ConnectionStatus.CONNECTING -> "در حال برقراری اتصال…"
                    ConnectionStatus.DISCONNECTING -> "در حال قطع…"
                    ConnectionStatus.DISCONNECTED -> "محافظت غیرفعال"
                },
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = statusColor
            )

            Spacer(Modifier.height(28.dp))

            // Big connect control
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(210.dp).testTag("connect_vpn_button")) {
                Box(
                    Modifier
                        .size(200.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.12f))
                        .border(2.dp, statusColor.copy(alpha = 0.35f), CircleShape)
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(148.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(statusColor, statusColor.copy(0.85f))))
                        .clickable { onToggleConnect() }
                        .border(3.dp, Color.White.copy(0.25f), CircleShape)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PowerSettingsNew, "اتصال", tint = Color.White, modifier = Modifier.size(52.dp))
                        Text(
                            if (connectionStatus == ConnectionStatus.CONNECTED) "قطع کن" else "وصل شو",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // Server picker row
            Card(
                onClick = onNavigateServerList,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().testTag("select_server_tile")
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedServer?.flagEmoji ?: "🌐", fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                selectedServer?.name ?: "انتخاب موقعیت سرور",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                buildString {
                                    append(selectedServer?.protocol ?: "—")
                                    selectedServer?.latencyMs?.let { append(" · ${it}ms") }
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(Icons.Default.KeyboardArrowLeft, null, tint = IfixAccent)
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat(Icons.Default.Timer, "مدت", formatDuration(vpnStats.durationSeconds), Modifier.weight(1f))
                MiniStat(Icons.Default.Download, "دانلود", String.format("%.0f KB/s", vpnStats.downloadSpeedKbps), Modifier.weight(1f))
                MiniStat(Icons.Default.Upload, "آپلود", String.format("%.0f KB/s", vpnStats.uploadSpeedKbps), Modifier.weight(1f))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MiniStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = IfixAccent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

@Composable
fun StatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    MiniStat(icon, label, value, modifier)
}
