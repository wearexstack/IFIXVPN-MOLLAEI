package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.models.RemoteConfig
import com.example.ui.theme.CyanPrimary

@Composable
fun UpdateDialog(
    remoteConfig: RemoteConfig,
    onDismiss: () -> Unit,
    onDownloadUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!remoteConfig.isForceUpdateRequired) {
                onDismiss()
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = CyanPrimary
            )
        },
        title = {
            Text(
                text = "به‌روزرسانی جدید IFIX VPN",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "نسخه ${remoteConfig.latestVersionName} آماده دانلود است.",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = remoteConfig.releaseNotes,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (remoteConfig.isForceUpdateRequired) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "این به‌روزرسانی برای ادامه استفاده اجباری است.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDownloadUpdate,
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color(0xFF0F172A))
            ) {
                Text("دانلود و نصب", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (!remoteConfig.isForceUpdateRequired) {
                TextButton(onClick = onDismiss) {
                    Text("بعداً")
                }
            }
        }
    )
}
