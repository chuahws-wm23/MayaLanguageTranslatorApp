package com.chuahws.mayalanguageapp.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6658),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EFE8),
    onPrimaryContainer = Color(0xFF0B3B32),
    secondary = Color(0xFF8A5A2B),
    secondaryContainer = Color(0xFFF2E3D3),
    background = Color(0xFFF8F7F3),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFECE9E2),
    outline = Color(0xFF777A74),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AD6C7),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF0B4F43),
    onPrimaryContainer = Color(0xFFB5F2E2),
    secondary = Color(0xFFE8BE92),
    secondaryContainer = Color(0xFF68411C),
    background = Color(0xFF111412),
    surface = Color(0xFF171B18),
)

@Composable
fun MayaTranslateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
