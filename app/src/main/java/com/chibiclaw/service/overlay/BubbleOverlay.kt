package com.chibiclaw.service.overlay

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Bubble overlay minimal (Phase 0).
 *
 * - Soft squircle, gray idle color (Phase 1 dynamic per state)
 * - Pulse animation halus (breathing)
 * - 56dp size
 *
 * Phase 1+ akan extend: status color per ChibiState, tap to expand chat panel,
 * drag handler + snap-to-edge.
 */
@Composable
fun BubbleOverlay() {
    val transition = rememberInfiniteTransition(label = "bubble-breathe")
    val scale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bubble-scale",
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFC9CFD9),  // light gray center
                        Color(0xFF8A93A6),  // darker gray rim
                    ),
                    center = Offset(20f, 18f),
                    radius = 60f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Phase 1+: glyph state. Phase 0 cuma dot subtle.
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.65f)),
        )
    }
}
