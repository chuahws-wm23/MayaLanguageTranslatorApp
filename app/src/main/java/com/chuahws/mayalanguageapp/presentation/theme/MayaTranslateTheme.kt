package com.chuahws.mayalanguageapp.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Archive palette. Legacy token names remain to preserve existing screen APIs.
val MayaPurple = Color(0xFF245744)
val MayaPurpleDark = Color(0xFF193D33)
val MayaLavender = Color(0xFFDCE8DE)
val MayaBlue = Color(0xFF4A6664)
val MayaGreen = Color(0xFF367360)
val MayaOrange = Color(0xFF8C682E)
val MayaPink = Color(0xFF8C6555)
val MayaCyan = Color(0xFF476F70)
val MayaRed = Color(0xFFAF5545)

val MayaBackground = Color(0xFFF7F5EC)
val MayaSurfaceSoft = Color(0xFFEAE5DA)
val MayaInk = Color(0xFF1D2824)

private val LightColors = lightColorScheme(
    primary = MayaPurple,
    onPrimary = Color.White,
    primaryContainer = MayaLavender,
    onPrimaryContainer = Color(0xFF15372C),

    secondary = MayaGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2EBDD),
    onSecondaryContainer = Color(0xFF203C2C),

    tertiary = MayaOrange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF1E5C9),
    onTertiaryContainer = Color(0xFF493513),

    background = MayaBackground,
    onBackground = MayaInk,

    surface = Color(0xFFFFFCF5),
    surfaceDim = Color(0xFFDED9CC),
    surfaceBright = Color(0xFFFFFCF5),
    surfaceContainerLowest = Color(0xFFFFFCF5),
    surfaceContainerLow = Color(0xFFF7F3E9),
    surfaceContainer = Color(0xFFF0ECE1),
    surfaceContainerHigh = Color(0xFFEAE5DA),
    surfaceContainerHighest = Color(0xFFE3DFD2),
    surfaceTint = MayaPurple,
    inverseSurface = Color(0xFF213533),
    inverseOnSurface = Color(0xFFF7F5EC),
    inversePrimary = Color(0xFF91F1BB),
    onSurface = MayaInk,
    surfaceVariant = MayaSurfaceSoft,
    onSurfaceVariant = Color(0xFF646A60),

    outline = Color(0xFFA9AE9F),
    outlineVariant = Color(0xFFD9D9C9),

    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

internal data class MayaAppearance(val dark: Boolean = false, val onChange: (Boolean) -> Unit = {})
internal val LocalMayaAppearance = staticCompositionLocalOf { MayaAppearance() }

private val DarkColors = darkColorScheme(
    primary = Color(0xFF91F1BB),
    onPrimary = Color(0xFF11382A),
    primaryContainer = Color(0xFF203E35),
    onPrimaryContainer = Color(0xFFD0EAD9),

    secondary = Color(0xFFB7CDB4),
    onSecondary = Color(0xFF253B27),
    secondaryContainer = Color(0xFF3A513B),
    onSecondaryContainer = Color(0xFFD2E7CC),

    tertiary = Color(0xFFFFB487),
    onTertiary = Color(0xFF3F2E0C),
    tertiaryContainer = Color(0xFF5A431D),
    onTertiaryContainer = Color(0xFFF7E1B3),

    background = Color(0xFF0D1516),
    onBackground = Color(0xFFEFFAF1),

    surface = Color(0xFF162223),
    surfaceDim = Color(0xFF0D1516),
    surfaceBright = Color(0xFF344138),
    surfaceContainerLowest = Color(0xFF0C1511),
    surfaceContainerLow = Color(0xFF15221B),
    surfaceContainer = Color(0xFF162223),
    surfaceContainerHigh = Color(0xFF213533),
    surfaceContainerHighest = Color(0xFF2D3A32),
    surfaceTint = Color(0xFF91F1BB),
    inverseSurface = Color(0xFFEFFAF1),
    inverseOnSurface = Color(0xFF162223),
    inversePrimary = MayaPurple,
    onSurface = Color(0xFFEFFAF1),
    surfaceVariant = Color(0xFF213533),
    onSurfaceVariant = Color(0xFFA8C0B5),

    outline = Color(0xFF8D9B8E),
    outlineVariant = Color(0xFF314843),

    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun MayaTranslateTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MayaTypography,
        shapes = MayaShapes,
        content = content,
    )
}
