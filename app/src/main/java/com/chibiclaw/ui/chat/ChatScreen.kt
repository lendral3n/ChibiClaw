package com.chibiclaw.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chibiclaw.agent.ConversationManager
import com.chibiclaw.data.database.TaskChannel
import com.chibiclaw.data.database.TaskEntity
import com.chibiclaw.data.database.TaskStatus
import com.chibiclaw.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Chat panel Phase 1 — text input, list task history (CHAT channel), tap untuk
 * detail.
 *
 * Phase 2 akan add mic button + voice input via STT.
 */
@Composable
fun ChatScreen(
    onOpenTask: (taskId: String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val tasks by viewModel.chatTasks.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(tasks.size) {
        if (tasks.isNotEmpty()) {
            listState.animateScrollToItem(tasks.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "Chat dengan Fuu",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tasks.isEmpty()) {
                item {
                    Text(
                        text = "Belum ada conversation. Coba ketik di bawah.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(tasks, key = { it.id }) { task ->
                ChatTaskCard(task = task, onClick = { onOpenTask(task.id) })
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ketik perintah ke Fuu...") },
                singleLine = false,
                keyboardActions = KeyboardActions(),
            )
            Spacer(Modifier.padding(4.dp))
            Button(
                onClick = {
                    if (input.isNotBlank()) {
                        scope.launch { viewModel.send(input) }
                        input = ""
                    }
                },
                enabled = input.isNotBlank(),
            ) {
                Text("Kirim")
            }
        }
    }
}

@Composable
private fun ChatTaskCard(task: TaskEntity, onClick: () -> Unit) {
    val isUserSide = true   // Phase 1: task representasi user message + Fuu response
    val statusLabel = when (task.status) {
        TaskStatus.PENDING -> "menunggu..."
        TaskStatus.PLANNING -> "Fuu lagi mikir..."
        TaskStatus.RUNNING -> "lagi kerja..."
        TaskStatus.BLOCKED -> "blocked"
        TaskStatus.AWAITING_USER -> "butuh input kamu"
        TaskStatus.COMPLETED -> "✓ ${task.resultSummary ?: "selesai"}"
        TaskStatus.FAILED -> "✗ ${task.errorMessage ?: "gagal"}"
        TaskStatus.CANCELLED -> "dibatalkan"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = task.goal,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val conversationManager: ConversationManager,
    taskRepository: TaskRepository,
) : ViewModel() {

    val chatTasks: StateFlow<List<TaskEntity>> = taskRepository.observeRecent(limit = 50)
        .let { flow ->
            kotlinx.coroutines.flow.MutableStateFlow(emptyList<TaskEntity>()).also { state ->
                viewModelScope.launch {
                    flow.collect { all ->
                        state.value = all.filter { it.channel == TaskChannel.CHAT }
                    }
                }
            }
        }

    suspend fun send(text: String) {
        conversationManager.handleUserInput(text)
    }
}
