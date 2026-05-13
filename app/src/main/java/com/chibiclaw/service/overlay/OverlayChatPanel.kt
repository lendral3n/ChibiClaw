package com.chibiclaw.service.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chibiclaw.agent.ConversationManager
import com.chibiclaw.data.database.TaskChannel
import com.chibiclaw.data.database.TaskEntity
import com.chibiclaw.data.database.TaskStatus
import com.chibiclaw.data.repository.TaskRepository
import com.chibiclaw.voice.VoicePipelineOrchestrator
import com.chibiclaw.voice.tts.ElevenLabsTts
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Embedded chat panel di overlay window — text input + mic button + chat
 * history list. Dipanggil saat user tap bubble.
 *
 * Phase 2: mic button trigger VoicePipelineOrchestrator (record → STT →
 * agent loop → TTS playback).
 */
@Composable
fun OverlayChatPanel(
    conversationManager: ConversationManager,
    taskRepository: TaskRepository,
    voicePipeline: VoicePipelineOrchestrator? = null,
    elevenLabsTts: ElevenLabsTts? = null,
    onClose: () -> Unit,
) {
    val tasksFlow = remember {
        taskRepository.observeRecent(limit = 20).map { all ->
            all.filter { it.channel == TaskChannel.CHAT }
        }
    }
    val tasks by tasksFlow.collectAsState(initial = emptyList())
    var input by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(tasks.size) {
        if (tasks.isNotEmpty()) listState.animateScrollToItem(tasks.size - 1)
    }

    DisposableEffect(Unit) {
        onDispose { voicePipeline?.stop() }
    }

    Card(
        modifier = Modifier
            .size(width = 360.dp, height = 520.dp)
            .padding(8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Fuu",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onClose) {
                    Text("×", style = MaterialTheme.typography.headlineMedium)
                }
            }

            if (elevenLabsTts != null && !elevenLabsTts.hasApiKey()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "ElevenLabs API key belum di-set. Setting → Voice (Phase 9 polish).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Chat history
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (tasks.isEmpty()) {
                    item {
                        Text(
                            text = "Tap input atau mic buat mulai.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(tasks, key = { it.id }) { task ->
                    OverlayTaskRow(task)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (voicePipeline != null) {
                    Button(
                        onClick = {
                            if (recording) {
                                voicePipeline.stop()
                                recording = false
                            } else {
                                voicePipeline.start(scope) { /* transcribed handled inside */ }
                                recording = true
                            }
                        },
                    ) {
                        Text(if (recording) "■" else "🎤")
                    }
                    Spacer(Modifier.size(4.dp))
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ketik...", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                )
                Spacer(Modifier.size(6.dp))
                Button(
                    onClick = {
                        if (input.isNotBlank()) {
                            val text = input
                            input = ""
                            scope.launch { conversationManager.handleUserInput(text) }
                        }
                    },
                    enabled = input.isNotBlank(),
                ) {
                    Text("→")
                }
            }
        }
    }
}

@Composable
private fun OverlayTaskRow(task: TaskEntity) {
    val statusLabel = when (task.status) {
        TaskStatus.PENDING -> "menunggu..."
        TaskStatus.PLANNING -> "mikir..."
        TaskStatus.RUNNING -> "kerja..."
        TaskStatus.AWAITING_USER -> "tanya kamu"
        TaskStatus.COMPLETED -> "✓ ${task.resultSummary ?: "selesai"}"
        TaskStatus.FAILED -> "✗ ${task.errorMessage ?: "gagal"}"
        TaskStatus.BLOCKED -> "blocked"
        TaskStatus.CANCELLED -> "dibatalkan"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
    ) {
        Column {
            Text(
                text = task.goal,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
