package com.ratig.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Full Material 3 color scheme.
 *
 * Every color role the UI reads is defined here (a missing role would silently
 * fall back to the purple M3 baseline, clashing with the RATIG green brand).
 * Roles are paired with their "on-"/"container" counterparts so text always
 * has a correct, accessible contrast partner.
 */
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

    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = SurfaceLight,
    onBackground = Color(0xFF181D18),
    surface = SurfaceLight,
    onSurface = Color(0xFF181D18),
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF414941),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F4EF),
    surfaceContainer = Color(0xFFEAEFE8),
    surfaceContainerHigh = Color(0xFFE4EAE3),
    surfaceContainerHighest = Color(0xFFDEE5DD),

    outline = Color(0xFF717971),
    outlineVariant = Color(0xFFC1C9BF),

    inverseSurface = Color(0xFF2D322D),
    inverseOnSurface = Color(0xFFEEF1EC),
    inversePrimary = GreenPrimaryDark,

    scrim = Color(0xFF000000),
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

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = SurfaceDark,
    onBackground = Color(0xFFDEE4DC),
    surface = SurfaceDark,
    onSurface = Color(0xFFDEE4DC),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BF),

    surfaceContainerLowest = Color(0xFF0B0F0B),
    surfaceContainerLow = Color(0xFF181D18),
    surfaceContainer = Color(0xFF1C211C),
    surfaceContainerHigh = Color(0xFF262B26),
    surfaceContainerHighest = Color(0xFF313631),

    outline = Color(0xFF8B938A),
    outlineVariant = Color(0xFF414941),

    inverseSurface = Color(0xFFDEE4DC),
    inverseOnSurface = Color(0xFF2D322D),
    inversePrimary = GreenPrimary,

    scrim = Color(0xFF000000),
)

/**
 * RATIG theme.
 *
 * [dynamicColor] opts into Material You (Android 12+), which derives the scheme
 * from the user's wallpaper. It is OFF by default: the app is an operational
 * tool where a stable, brand-consistent, high-contrast palette matters more
 * than personalization, and fixed colors guarantee legible risk severity cues.
 *
 * [textScale] multiplies every text style so users who need larger text can
 * enlarge the whole UI from the setup screen.
 */
@Composable
fun RatigTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RatigTypography.scaled(textScale),
        shapes = RatigShapes,
        content = content,
    )
}
