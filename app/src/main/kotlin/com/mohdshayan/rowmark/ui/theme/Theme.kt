package com.mohdshayan.rowmark.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightOnAccent,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    onError = LightOnAccent,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = LightOnBackground,
    secondary = LightOnSurfaceVariant,
    onSecondary = LightSurface,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightOnBackground,
    tertiary = LightAccent,
    onTertiary = LightOnAccent,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurface,
    surfaceContainerHighest = LightSurfaceVariant,
    surfaceContainerLow = LightSurface,
    surfaceContainerLowest = LightSurface,
    inverseSurface = DarkSurface,
    inverseOnSurface = DarkOnBackground,
    inversePrimary = DarkAccent,
    outlineVariant = LightOutline.copy(alpha = DividerAlpha),
    error = LightError,
)

private val DarkColors = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkOnAccent,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    onError = DarkOnAccent,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = DarkOnBackground,
    secondary = DarkOnSurfaceVariant,
    onSecondary = DarkSurface,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkOnBackground,
    tertiary = DarkAccent,
    onTertiary = DarkOnAccent,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurface,
    surfaceContainerHighest = DarkSurfaceVariant,
    surfaceContainerLow = DarkSurface,
    surfaceContainerLowest = DarkSurface,
    inverseSurface = LightSurface,
    inverseOnSurface = LightOnBackground,
    inversePrimary = LightAccent,
    outlineVariant = DarkOutline.copy(alpha = DividerAlpha),
    error = DarkError,
)

/**
 * The app theme. Dynamic colour is deliberately off: the accent is part of the
 * app's identity and must look the same on every device. System bar icons follow
 * the mode, and LocalReducedMotion is provided for every animation to consult.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
