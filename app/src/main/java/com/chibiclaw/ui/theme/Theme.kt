package com.chibiclaw.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = ChibiRose,
    onPrimary = ChibiSurface,
    primaryContainer = ChibiRoseLight,
    onPrimaryContainer = ChibiRoseDark,
    secondary = ChibiRose,
    background = ChibiBackground,
    onBackground = ChibiTextPrimary,
    surface = ChibiSurface,
    onSurface = ChibiTextPrimary,
    surfaceVariant = ChibiSurfaceVariant,
    onSurfaceVariant = ChibiTextSecondary,
    outline = ChibiBorder,
    outlineVariant = ChibiSurfaceVariant,
    error = StateError,
)

private val DarkColorScheme = darkColorScheme(
    primary = ChibiRose,
    onPrimary = ChibiSurfaceDark,
    primaryContainer = ChibiRoseDark,
    onPrimaryContainer = ChibiRoseLight,
    secondary = ChibiRose,
    background = ChibiBackgroundDark,
    onBackground = ChibiTextPrimaryDark,
    surface = ChibiSurfaceDark,
    onSurface = ChibiTextPrimaryDark,
    surfaceVariant = ChibiSurfaceVariantDark,
    onSurfaceVariant = ChibiTextSecondaryDark,
    outline = ChibiBorderDark,
    outlineVariant = ChibiSurfaceVariantDark,
    error = StateError,
)

@Composable
fun ChibiClawTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ChibiTypography,
        content = content,
    )
}
