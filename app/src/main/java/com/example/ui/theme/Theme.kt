package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = IfixAccent,
    onPrimary = Color.White,
    primaryContainer = IfixAccentSoft,
    onPrimaryContainer = Color.White,
    secondary = IfixMint,
    onSecondary = IfixBg,
    background = IfixBg,
    onBackground = LightTextPrimary,
    surface = IfixSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = IfixCard,
    onSurfaceVariant = LightTextSecondary,
    outline = IfixBorder,
    error = StatusOff
)

private val LightScheme = lightColorScheme(
    primary = IfixAccent,
    onPrimary = Color.White,
    primaryContainer = IfixAccentSoft,
    onPrimaryContainer = IfixBg,
    secondary = IfixMint,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = DarkTextPrimary,
    surface = LightSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = LightCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = LightBorder,
    error = StatusOff
)

@Composable
fun IfixVpnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    IfixVpnTheme(darkTheme = darkTheme, content = content)
}
