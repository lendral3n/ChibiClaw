package com.chibiclaw.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chibiclaw.ai.EngineState
import com.chibiclaw.core.ChatMessage
import com.chibiclaw.gateway.source.VoiceState
import com.chibiclaw.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val engineState by viewModel.engineState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll when messages or streaming text changes
    LaunchedEffect(messages.size, streamingText.length) {
        val totalItems = messages.size + if (streamingText.isNotEmpty() || isBusy) 1 else 0
        if (totalItems > 0) {
            coroutineScope.launch {
                listState.animateScrollToItem(totalItems - 1)
            }
        }
    }

    val statusText = when {
        engineState == EngineState.UNLOADED -> "Model belum dimuat"
        engineState == EngineState.LOADING -> "Memuat model..."
        engineState == EngineState.ERROR -> "Gagal memuat model"
        isBusy -> "Fuu sedang berpikir..."
        else -> "Ready"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Fuu",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                engineState == EngineState.ERROR -> MaterialTheme.colorScheme.error
                                isBusy -> MaterialTheme.colorScheme.primary
                                engineState == EngineState.READY -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Chat message list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty() && !isBusy) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Halo! Aku Fuu\uD83D\uDC3E\nKetik perintah dan aku akan menjalankannya.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                items(messages) { message ->
                    ChatBubble(message = message)
                }
                // Show streaming text as in-progress Fuu bubble
                if (streamingText.isNotEmpty()) {
                    item {
                        ChatBubble(
                            message = ChatMessage(streamingText, isUser = false),
                            isStreaming = true
                        )
                    }
                } else if (isBusy) {
                    // Typing indicator
                    item { TypingIndicator() }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Ketik perintah untuk Fuu...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    enabled = !isBusy && engineState == EngineState.READY,
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Voice button
                IconButton(
                    onClick = { viewModel.toggleVoice() },
                    enabled = !isBusy && engineState == EngineState.READY
                ) {
                    Icon(
                        if (voiceState == VoiceState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice",
                        tint = if (voiceState == VoiceState.LISTENING) StateError else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Send / Stop button — swaps into Stop while a turn is
                // in-flight so the user always has a brake pedal (BUG-B).
                // KillSwitch.activate() (via stopCommand) unblocks the
                // orchestrator's streaming loop and resets the FSM.
                if (isBusy) {
                    FilledIconButton(
                        onClick = { viewModel.stopCommand() },
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = StateError
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                } else {
                    FilledIconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                viewModel.sendCommand(input.trim())
                                input = ""
                            }
                        },
                        enabled = input.isNotBlank() && engineState == EngineState.READY,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    isStreaming: Boolean = false
) {
    val isUser = message.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            // Fuu avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Purple40),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "F",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnBackgroundDark
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = if (isUser) UserBubble else FuuBubble,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = if (isStreaming) "${message.text}\u258A" else message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) OnBackgroundDark else OnSurfaceDark,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Purple40),
            contentAlignment = Alignment.Center
        ) {
            Text("F", style = MaterialTheme.typography.labelMedium, color = OnBackgroundDark)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            color = FuuBubble
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(OnSurfaceVariant)
                    )
                }
            }
        }
    }
}
