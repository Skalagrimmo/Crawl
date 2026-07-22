package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CyberColorScheme = darkColorScheme(
    primary = CyberPrimary,
    secondary = CyberSecondary,
    tertiary = CyberTertiary,
    background = CyberBg,
    surface = CyberSurface,
    onPrimary = CyberBg,
    onSecondary = CyberBg,
    onTertiary = CyberBg,
    onBackground = CyberText,
    onSurface = CyberText
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme by default for the retro cyberpunk aesthetic
    dynamicColor: Boolean = false, // Disable dynamic colors to enforce the neon terminal scheme
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CyberColorScheme,
        typography = Typography,
        content = content
    )
}
