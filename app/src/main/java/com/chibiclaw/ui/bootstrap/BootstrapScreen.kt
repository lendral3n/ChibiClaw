package com.chibiclaw.ui.bootstrap

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chibiclaw.core.ChibiBootstrapper
import com.chibiclaw.ui.theme.*

/**
 * Phase 10 — "Loading..." screen that sits between mode selection
 * and the main dashboard. Kicks off [ChibiBootstrapper.bootstrap]
 * on first composition and navigates forward once the engine reports
 * READY (or fails gracefully with a retry button).
 *
 * This screen is the **only** place where ChibiService is started
 * and the Gemma model is loaded. Everything upstream (splash, mode
 * selection) must remain light so it opens instantly.
 */
@Composable
fun BootstrapScreen(
    modeLabel: String,
    onReady: () -> Unit,
    viewModel: BootstrapViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Fire the bootstrap pipeline exactly once on first composition.
    // Re-subscribes after config change are harmless — bootstrap() is
    // idempotent when the engine is already Ready.
    LaunchedEffect(Unit) {
        viewModel.start()
    }

    // Navigate to home when bootstrap reaches Ready state.
    // LaunchedEffect runs on Main dispatcher — safe for NavController.
    LaunchedEffect(state) {
        if (state is ChibiBootstrapper.State.Ready) {
            onReady()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF08080F), Color(0xFF0D0D1E), Color(0xFF08080F))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Pulsing mascot
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulse by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (state is ChibiBootstrapper.State.Failed) 1f else 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_scale"
            )
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(Purple40.copy(alpha = 0.15f))
                    .border(1.dp, Purple40.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🐱", fontSize = 56.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "ChibiClaw",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = OnBackgroundDark
            )
            Text(
                "Mode: $modeLabel",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Step list
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BootstrapStep(
                    label = "Menyiapkan skill & whitelist",
                    status = StepStatus.Done
                )
                BootstrapStep(
                    label = "Menghidupkan ChibiService",
                    status = when (state) {
                        ChibiBootstrapper.State.Idle -> StepStatus.Pending
                        ChibiBootstrapper.State.ServiceStarting -> StepStatus.InProgress
                        ChibiBootstrapper.State.ModelLoading,
                        ChibiBootstrapper.State.Ready -> StepStatus.Done
                        is ChibiBootstrapper.State.Failed -> StepStatus.Failed
                    }
                )
                BootstrapStep(
                    label = "Memuat model Gemma",
                    status = when (state) {
                        ChibiBootstrapper.State.Idle,
                        ChibiBootstrapper.State.ServiceStarting -> StepStatus.Pending
                        ChibiBootstrapper.State.ModelLoading -> StepStatus.InProgress
                        ChibiBootstrapper.State.Ready -> StepStatus.Done
                        is ChibiBootstrapper.State.Failed -> StepStatus.Failed
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Status line + retry button
            when (val s = state) {
                is ChibiBootstrapper.State.Failed -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = StateError.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = StateError
                                )
                                Text(
                                    "Bootstrap gagal",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StateError
                                )
                            }
                            Text(
                                s.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant,
                                textAlign = TextAlign.Start
                            )
                            Button(
                                onClick = { viewModel.retry() },
                                colors = ButtonDefaults.buttonColors(containerColor = Purple40),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Coba Lagi") }
                        }
                    }
                }
                ChibiBootstrapper.State.Ready -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StateCompleted
                        )
                        Text(
                            "Siap",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StateCompleted
                        )
                    }
                }
                else -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(3.dp),
                        color = Purple40,
                        trackColor = Purple40.copy(alpha = 0.2f)
                    )
                }
            }
        }

        Text(
            "Heavy init berjalan setelah mode dipilih",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF333350)
        )
    }
}

private enum class StepStatus { Pending, InProgress, Done, Failed }

@Composable
private fun BootstrapStep(label: String, status: StepStatus) {
    val (dotColor, text) = when (status) {
        StepStatus.Pending -> StateIdle to "•"
        StepStatus.InProgress -> StatePlanning to "⟳"
        StepStatus.Done -> StateCompleted to "✓"
        StepStatus.Failed -> StateError to "✗"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = 0.18f))
                .border(1.dp, dotColor.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = dotColor
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = when (status) {
                StepStatus.Pending -> OnSurfaceVariant
                StepStatus.InProgress -> OnBackgroundDark
                StepStatus.Done -> StateCompleted
                StepStatus.Failed -> StateError
            }
        )
    }
}
