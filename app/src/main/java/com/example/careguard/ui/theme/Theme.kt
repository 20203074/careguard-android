package com.example.careguard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF005EBC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E9FF),
    secondary = Color(0xFF006D3B),
    error = Color(0xFFB42318),
    background = Color(0xFFF8F9FC),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA6C8FF),
    secondary = Color(0xFF6DDB9C),
    error = Color(0xFFFFB4AB)
)

@Composable
fun CareGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
