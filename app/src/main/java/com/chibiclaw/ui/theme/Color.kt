package com.chibiclaw.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color palette ChibiClaw Phase 0. Kawaii pastel.
 *
 * Phase 9 polish akan migrate ke OKLCH token system (CCColors di v4 design).
 * Phase 0 baseline Material 3 palette dengan accent rose.
 */

// Primary (rose accent)
val ChibiRose = Color(0xFFD96B91)
val ChibiRoseLight = Color(0xFFFAD4E0)
val ChibiRoseDark = Color(0xFF8A2E50)

// Surface
val ChibiBackground = Color(0xFFFAF7FB)
val ChibiSurface = Color(0xFFFFFFFF)
val ChibiSurfaceVariant = Color(0xFFF1ECF5)

// Text
val ChibiTextPrimary = Color(0xFF2A2A35)
val ChibiTextSecondary = Color(0xFF6E6E7A)
val ChibiTextTertiary = Color(0xFF9A9AA8)

// Border / divider
val ChibiBorder = Color(0xFFE5E0EA)

// State colors (basic, Phase 9 dynamic via OKLCH per ChibiState)
val StateIdle = Color(0xFF8A93A6)
val StatePlanning = Color(0xFF5B91CD)
val StateExecuting = Color(0xFF6BC298)
val StateWaiting = Color(0xFFE3B96B)
val StateError = Color(0xFFD97B7B)
val StateComplete = Color(0xFFB47BD9)

// Dark mode
val ChibiBackgroundDark = Color(0xFF1A1820)
val ChibiSurfaceDark = Color(0xFF252330)
val ChibiSurfaceVariantDark = Color(0xFF2E2B3A)
val ChibiTextPrimaryDark = Color(0xFFF0EEF3)
val ChibiTextSecondaryDark = Color(0xFFBABAC9)
val ChibiBorderDark = Color(0xFF38353F)
