package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.VpnDisconnectedRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseActivationScreen(
    licenseError: String?,
    licenseSuccessMsg: String?,
    onActivateKey: (String) -> Unit,
    onNavigateHome: () -> Unit,
    onClearMessages: () -> Unit
) {
    var keyInput by remember { mutableStateOf("") }
    var showDemoKeysSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val demoKeys = listOf(
        "IFIX-VIP-PRO-2026" to "Commercial 1-Year VIP (All Servers)",
        "IFIX-PREMIUM-9999" to "Ultra High-Speed Pass (5 Devices)",
        "IFIX-DEMO-TEST" to "Tester Trial License (30 Days)"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("license_activation_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header Logo Badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(2.dp, CyanPrimary, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock Icon",
                        tint = CyanPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "License Activation",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "Please enter your valid IFIX VPN License Key to activate commercial servers.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )

                // Error alert card
                if (licenseError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x22EF4444)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = VpnDisconnectedRed
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = licenseError,
                                color = VpnDisconnectedRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Success alert card
                if (licenseSuccessMsg != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x2210B981)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF10B981)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = licenseSuccessMsg,
                                color = Color(0xFF10B981),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Key Text Input
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = {
                        keyInput = it
                        onClearMessages()
                    },
                    label = { Text("License Key (e.g. IFIX-XXXX-YYYY)") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Key Icon",
                            tint = CyanPrimary
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { keyInput = "IFIX-VIP-PRO-2026" }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Paste Demo Key",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = CyanPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("license_key_input")
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        onActivateKey(keyInput)
                        if (keyInput.isNotBlank() && !keyInput.contains("INVALID")) {
                            onNavigateHome()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = Color(0xFF0F172A)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("activate_license_button")
                ) {
                    Text(
                        text = "ACTIVATE LICENSE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = { showDemoKeysSheet = true }) {
                    Text(
                        text = "Need a Demo License Key?",
                        color = CyanSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showDemoKeysSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDemoKeysSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Select Demo Test License Key",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Tap any license key below to insert into activation form:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(demoKeys) { (key, desc) ->
                        Card(
                            onClick = {
                                keyInput = key
                                showDemoKeysSheet = false
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = key,
                                        fontWeight = FontWeight.Bold,
                                        color = CyanPrimary,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = desc,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = CyanSecondary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
