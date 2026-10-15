package com.chuahws.mayalanguageapp.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MayaPurple = Color(0xFF6C3BEF)
val MayaPurpleDark = Color(0xFF4C1FB8)
val MayaLavender = Color(0xFFF0E9FF)
val MayaBlue = Color(0xFF3B82F6)
val MayaGreen = Color(0xFF18A875)
val MayaOrange = Color(0xFFF59E42)
val MayaPink = Color(0xFFE85AAD)
val MayaCyan = Color(0xFF29A7C9)
val MayaRed = Color(0xFFE75454)

val MayaBackground = Color(0xFFF8F6FC)
val MayaSurfaceSoft = Color(0xFFF4F0FC)
val MayaInk = Color(0xFF211A2C)

private val LightColors = lightColorScheme(
    primary = MayaPurple,
    onPrimary = Color.White,
    primaryContainer = MayaLavender,
    onPrimaryContainer = Color(0xFF29104F),
    secondary = Color(0xFF71558F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0E7F7),
    tertiary = MayaCyan,
    background = MayaBackground,
    onBackground = MayaInk,
    surface = Color.White,
    onSurface = MayaInk,
    surfaceVariant = MayaSurfaceSoft,
    onSurfaceVariant = Color(0xFF6C6476),
    outline = Color(0xFFC9C0D4),
    outlineVariant = Color(0xFFE3DDEA),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD6B9FF),
    onPrimary = Color(0xFF351067),
    primaryContainer = Color(0xFF50309A),
    onPrimaryContainer = Color(0xFFF0E4FF),
    secondary = Color(0xFFDCC2EA),
    secondaryContainer = Color(0xFF4F3B5D),
    tertiary = Color(0xFF83D5EA),
    background = Color(0xFF151218),
    onBackground = Color(0xFFF0EAF3),
    surface = Color(0xFF1E1A22),
    onSurface = Color(0xFFF0EAF3),
    surfaceVariant = Color(0xFF332D39),
    onSurfaceVariant = Color(0xFFD1C8D7),
    outline = Color(0xFF998FA0),
    error = Color(0xFFFFB4AB),
)

@Composable
fun MayaTranslateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MayaTypography,
        shapes = MayaShapes,
        content = content,
    )
}
