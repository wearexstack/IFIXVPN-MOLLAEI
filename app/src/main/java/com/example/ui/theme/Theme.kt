package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = DarkNavyBackground,
    primaryContainer = CyanSecondary,
    onPrimaryContainer = Color.White,
    secondary = CyanSecondary,
    onSecondary = Color.White,
    background = DarkNavyBackground,
    onBackground = LightTextPrimary,
    surface = DarkNavySurface,
    onSurface = LightTextPrimary,
    surfaceVariant = DarkNavyCard,
    onSurfaceVariant = LightTextSecondary,
    outline = DarkNavyBorder,
    error = VpnDisconnectedRed
)

private val LightColorScheme = lightColorScheme(
    primary = CyanSecondary,
    onPrimary = Color.White,
    primaryContainer = CyanPrimary,
    onPrimaryContainer = DarkNavyBackground,
    secondary = DarkNavySurface,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = DarkTextPrimary,
    surface = LightSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = LightCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = LightBorder,
    error = VpnDisconnectedRed
)

@Composable
fun IfixVpnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Retain alias for backward compatibility
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    IfixVpnTheme(darkTheme = darkTheme, content = content)
}

