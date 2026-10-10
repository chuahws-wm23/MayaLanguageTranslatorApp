package com.chuahws.mayalanguageapp.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MayaPurple = Color(0xFF6F2DBD)
val MayaPurpleDark = Color(0xFF54208F)
val MayaLavender = Color(0xFFF3E9FF)
val MayaBlue = Color(0xFF2878C8)
val MayaGreen = Color(0xFF2E9C67)
val MayaOrange = Color(0xFFF59E3D)
val MayaPink = Color(0xFFCC4C9A)

private val LightColors = lightColorScheme(
    primary = MayaPurple,
    onPrimary = Color.White,
    primaryContainer = MayaLavender,
    onPrimaryContainer = Color(0xFF2E0B4C),
    secondary = Color(0xFF73558B),
    secondaryContainer = Color(0xFFF0E4F7),
    background = Color(0xFFFAF8FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFF2EFF4),
    outline = Color(0xFF817784),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD7B8FF),
    onPrimary = Color(0xFF3D006D),
    primaryContainer = Color(0xFF56208B),
    onPrimaryContainer = Color(0xFFF0DBFF),
    secondary = Color(0xFFDCC0EA),
    background = Color(0xFF151218),
    surface = Color(0xFF1D1A20),
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
