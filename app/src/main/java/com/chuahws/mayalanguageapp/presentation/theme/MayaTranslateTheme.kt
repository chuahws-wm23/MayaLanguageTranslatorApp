package com.chuahws.mayalanguageapp.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand colours: modern indigo + teal, with warmer supporting accents.
val MayaPurple = Color(0xFF5B50E6)
val MayaPurpleDark = Color(0xFF4036B8)
val MayaLavender = Color(0xFFE9E7FF)
val MayaBlue = Color(0xFF4776E6)
val MayaGreen = Color(0xFF119C84)
val MayaOrange = Color(0xFFF08A4B)
val MayaPink = Color(0xFFD85F9E)
val MayaCyan = Color(0xFF2B9EB3)
val MayaRed = Color(0xFFD95151)

val MayaBackground = Color(0xFFF7F8FC)
val MayaSurfaceSoft = Color(0xFFF0F2F8)
val MayaInk = Color(0xFF1B1B24)

private val LightColors = lightColorScheme(
    primary = MayaPurple,
    onPrimary = Color.White,
    primaryContainer = MayaLavender,
    onPrimaryContainer = Color(0xFF201A55),

    secondary = MayaGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF5EF),
    onSecondaryContainer = Color(0xFF073D35),

    tertiary = MayaOrange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8D8),
    onTertiaryContainer = Color(0xFF4A210B),

    background = MayaBackground,
    onBackground = MayaInk,

    surface = Color(0xFFFFFFFF),
    onSurface = MayaInk,
    surfaceVariant = MayaSurfaceSoft,
    onSurfaceVariant = Color(0xFF666975),

    outline = Color(0xFFD6D9E5),
    outlineVariant = Color(0xFFE7E9F0),

    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC8C3FF),
    onPrimary = Color(0xFF292174),
    primaryContainer = Color(0xFF3D35A5),
    onPrimaryContainer = Color(0xFFE7E4FF),

    secondary = Color(0xFF84D8C6),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF0E5146),
    onSecondaryContainer = Color(0xFFA4F3E1),

    tertiary = Color(0xFFFFB68D),
    onTertiary = Color(0xFF552109),
    tertiaryContainer = Color(0xFF783817),
    onTertiaryContainer = Color(0xFFFFDBCA),

    background = Color(0xFF121318),
    onBackground = Color(0xFFE8E8EF),

    surface = Color(0xFF1A1B21),
    onSurface = Color(0xFFE8E8EF),
    surfaceVariant = Color(0xFF272930),
    onSurfaceVariant = Color(0xFFC7C9D3),

    outline = Color(0xFF8F919C),
    outlineVariant = Color(0xFF3B3D46),

    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
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
