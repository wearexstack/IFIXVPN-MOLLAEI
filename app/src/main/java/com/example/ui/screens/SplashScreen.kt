package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavySurface
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    isLicenseActive: Boolean,
    licenseReady: Boolean = true,
    onSplashFinished: (destination: String) -> Unit
) {
    var startAnimation by remember { mutableStateOf(false) }
    var navigated by remember { mutableStateOf(false) }

    val scaleAnimation by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.4f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "logo_scale"
    )

    val alphaAnimation by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "logo_alpha"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    // Wait until Room license is loaded, then route once
    LaunchedEffect(licenseReady, isLicenseActive) {
        if (!licenseReady || navigated) return@LaunchedEffect
        delay(900)
        navigated = true
        if (isLicenseActive) {
            onSplashFinished("home")
        } else {
            onSplashFinished("license")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DarkNavyBackground,
                        DarkNavySurface,
                        Color(0xFF070B14)
                    )
                )
            )
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scaleAnimation)
                .alpha(alphaAnimation)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(DarkNavySurface)
                    .border(3.dp, CyanPrimary, CircleShape)
                    .border(8.dp, CyanGlow, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "IFIX VPN",
                    tint = CyanPrimary,
                    modifier = Modifier.size(72.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "IFIX VPN",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = CyanPrimary,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (!licenseReady) "در حال بارگذاری…" else "نسخه تجاری VIP",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
