package com.ratig.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9E4BB),
    onPrimaryContainer = Color(0xFF04290A),
    secondary = TealSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB4CCC7),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = AmberTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB2),
    onTertiaryContainer = Color(0xFF271900),
    background = SurfaceLight,
    onBackground = Color(0xFF181D18),
    surface = SurfaceLight,
    onSurface = Color(0xFF181D18),
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF414941),
    outline = Color(0xFF717971),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = Color(0xFF0B3D11),
    primaryContainer = Color(0xFF275D2D),
    onPrimaryContainer = Color(0xFFCFEBCF),
    secondary = TealSecondaryDark,
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Color(0xFFA0F0E5),
    tertiary = AmberTertiaryDark,
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4300),
    onTertiaryContainer = Color(0xFFFFDFB0),
    background = SurfaceDark,
    onBackground = Color(0xFFDEE4DC),
    surface = SurfaceDark,
    onSurface = Color(0xFFDEE4DC),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BF),
    outline = Color(0xFF8B938A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun RatigTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = RatigTypography,
        content = content,
    )
}
