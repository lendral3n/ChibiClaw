package com.chibiclaw.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ChibiDarkColorScheme = darkColorScheme(
    primary = Purple40,
    onPrimary = OnBackgroundDark,
    primaryContainer = Purple80.copy(alpha = 0.15f),
    onPrimaryContainer = PurpleLight,
    secondary = Teal40,
    onSecondary = OnBackgroundDark,
    secondaryContainer = Teal80.copy(alpha = 0.15f),
    onSecondaryContainer = Teal80,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariant,
    error = StateError,
    onError = OnBackgroundDark,
    errorContainer = StateError.copy(alpha = 0.15f),
    onErrorContainer = StateError,
    outline = OnSurfaceVariant.copy(alpha = 0.5f),
    outlineVariant = SurfaceVariantDark,
)

@Composable
fun ChibiClawTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChibiDarkColorScheme,
        typography = ChibiTypography,
        content = content
    )
}
