package com.chibiclaw.ui.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chibiclaw.ai.EngineState
import com.chibiclaw.executor.StepRunner
import com.chibiclaw.memory.local.entity.CommandHistory
import com.chibiclaw.state.ChibiState
import com.chibiclaw.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToChat: () -> Unit,
    onNavigateToPermissions: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val engineState by viewModel.engineState.collectAsState()
    val lastError by viewModel.lastError.collectAsState()
    val history by viewModel.history.collectAsState()
    val recentSteps by viewModel.recentSteps.collectAsState()
    val accessibilityConnected by viewModel.accessibilityConnected.collectAsState()
    val undoCount by viewModel.undoCount.collectAsState()
    val undoToast by viewModel.undoToast.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface undo result as a transient snackbar so the user gets
    // feedback without cluttering the dashboard.
    LaunchedEffect(undoToast) {
        val msg = undoToast ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearUndoToast()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "ChibiClaw",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToPermissions) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = "Permissions"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // P10 — Undo FAB. Only shown when the UndoStack is
                // non-empty; pops the most recent reversible action
                // off the stack and runs its reverse lambda.
                if (undoCount > 0) {
                    SmallFloatingActionButton(
                        onClick = { viewModel.undoLast() },
                        containerColor = StateError.copy(alpha = 0.85f),
                        contentColor = Color.White
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo ($undoCount)"
                        )
                    }
                }
                ExtendedFloatingActionButton(
                    onClick = onNavigateToChat,
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                    text = { Text("Chat") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Model status card
            item {
                ModelStatusCard(
                    engineState = engineState,
                    modelFileName = viewModel.modelFileName,
                    backend = viewModel.modelBackend
                )
            }

            // BUG-L: Accessibility indicator — surfaces whether UI actions
            // (scroll/click/type inside other apps) will actually work.
            // Without this, "buka tiktok lalu scroll" silently fails with
            // a generic error because the user doesn't realise the a11y
            // service got killed by battery optimisation.
            item {
                AccessibilityIndicator(connected = accessibilityConnected)
            }

            // Agent state card
            item {
                AgentStateCard(
                    state = state,
                    lastError = lastError,
                    onStop = { viewModel.stopTask() }
                )
            }

            // P4.3 — Step timeline: live view of the StepRunner events emitted
            // by ChibiOrchestrator during command processing. Rendered only when
            // there's something to show AND the agent is still working (or just
            // finished) so IDLE screens stay clean.
            if (recentSteps.isNotEmpty() && state != ChibiState.IDLE) {
                item {
                    StepTimelineCard(steps = recentSteps)
                }
            }

            // Stats row
            item {
                StatsRow(history = history)
            }

            // Recent commands header
            item {
                Text(
                    "Perintah Terbaru",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (history.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            "Belum ada perintah. Coba Chat dengan Fuu!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(history) { item ->
                CommandHistoryCard(item = item)
            }

            // FAB spacer
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun ModelStatusCard(
    engineState: EngineState,
    modelFileName: String,
    backend: String
) {
    val (statusColor, statusLabel, statusIcon) = when (engineState) {
        EngineState.READY -> Triple(StateCompleted, "READY", Icons.Default.CheckCircle)
        EngineState.LOADING -> Triple(StatePlanning, "LOADING", Icons.Default.Memory)
        EngineState.ERROR -> Triple(StateError, "ERROR", Icons.Default.Error)
        EngineState.UNLOADED -> Triple(StateIdle, "UNLOADED", Icons.Default.Memory)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    modelFileName.ifEmpty { "No model" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "$backend · $statusLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }
            if (engineState == EngineState.LOADING) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = statusColor)
            }
        }
    }
}

@Composable
private fun AgentStateCard(
    state: ChibiState,
    lastError: String?,
    onStop: () -> Unit
) {
    val isExecuting = state == ChibiState.EXECUTING
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isExecuting) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val (stateColor, stateLabel) = when (state) {
        ChibiState.IDLE -> StateIdle to "Idle"
        ChibiState.PLANNING -> StatePlanning to "Merencanakan..."
        ChibiState.EXECUTING -> StateExecuting to "Menjalankan..."
        ChibiState.VERIFYING -> StatePlanning to "Memverifikasi..."
        ChibiState.ERROR_RECOVERY -> StateError to "Error Recovery"
        ChibiState.WAITING_USER -> StateWaiting to "Menunggu konfirmasi..."
        ChibiState.PAUSED -> StatePaused to "Dijeda"
        ChibiState.COMPLETED -> StateCompleted to "Selesai"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pulseScale),
        colors = CardDefaults.cardColors(
            containerColor = stateColor.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(stateColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Agent State",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stateLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = stateColor
                )
                if (lastError != null) {
                    Text(
                        lastError,
                        style = MaterialTheme.typography.bodySmall,
                        color = StateError
                    )
                }
            }
            if (state != ChibiState.IDLE && state != ChibiState.COMPLETED) {
                OutlinedButton(
                    onClick = onStop,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StateError),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StateError)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun AccessibilityIndicator(connected: Boolean) {
    val bg = if (connected) StateCompleted.copy(alpha = 0.12f) else StateError.copy(alpha = 0.12f)
    val fg = if (connected) StateCompleted else StateError
    val label = if (connected) "Accessibility aktif — perintah UI tersedia"
                else "Accessibility tidak aktif — perintah scroll/click tidak akan bekerja"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(fg)
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = fg
            )
        }
    }
}

@Composable
private fun StatsRow(history: List<CommandHistory>) {
    val total = history.size
    val successCount = history.count { it.state == "COMPLETED" }
    val successRate = if (total > 0) (successCount * 100 / total) else 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            label = "Tasks",
            value = total.toString(),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "Success Rate",
            value = "$successRate%",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "Errors",
            value = history.count { it.state == "ERROR" }.toString(),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StepTimelineCard(steps: List<StepRunner.StepLog>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Step Timeline",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            steps.takeLast(6).forEach { step ->
                val (dotColor, label) = when (step.state) {
                    "RUNNING" -> StatePlanning to "▶"
                    "DONE" -> StateCompleted to "✓"
                    "FAILED" -> StateError to "✗"
                    "ERROR" -> StateError to "!"
                    else -> StateIdle to "•"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = dotColor,
                        modifier = Modifier.width(16.dp)
                    )
                    Text(
                        "#${step.stepNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(
                        step.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandHistoryCard(item: CommandHistory) {
    val (statusColor, statusLabel) = when (item.state) {
        "COMPLETED" -> StateCompleted to "OK"
        "ERROR" -> StateError to "ERR"
        "BLOCKED" -> StateWaiting to "BLK"
        else -> StateIdle to item.state
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = statusColor.copy(alpha = 0.15f)
            ) {
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.command,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    item.result,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
