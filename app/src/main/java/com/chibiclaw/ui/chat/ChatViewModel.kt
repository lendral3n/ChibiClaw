package com.chibiclaw.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chibiclaw.ai.EngineState
import com.chibiclaw.ai.GemmaEngineManager
import com.chibiclaw.core.ChatMessage
import com.chibiclaw.core.ChibiOrchestrator
import com.chibiclaw.gateway.CommandGateway
import com.chibiclaw.gateway.CommandSource
import com.chibiclaw.gateway.source.VoiceCommandSource
import com.chibiclaw.gateway.source.VoiceState
import com.chibiclaw.state.ChibiState
import com.chibiclaw.state.StateObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val commandGateway: CommandGateway,
    private val orchestrator: ChibiOrchestrator,
    private val engineManager: GemmaEngineManager,
    private val voiceCommandSource: VoiceCommandSource,
    stateObserver: StateObserver
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> = orchestrator.messages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isBusy: StateFlow<Boolean> = stateObserver.state
        .map { it != ChibiState.IDLE && it != ChibiState.COMPLETED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val engineState: StateFlow<EngineState> = engineManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EngineState.UNLOADED)

    val voiceState: StateFlow<VoiceState> = voiceCommandSource.voiceState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VoiceState.IDLE)

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText

    init {
        viewModelScope.launch {
            orchestrator.streamingChunk.collect { chunk ->
                _streamingText.value += chunk
            }
        }
        viewModelScope.launch {
            stateObserver.state.collect { state ->
                if (state == ChibiState.IDLE || state == ChibiState.COMPLETED) {
                    _streamingText.value = ""
                }
            }
        }
    }

    fun sendCommand(text: String) {
        _streamingText.value = ""
        viewModelScope.launch {
            commandGateway.submitDirect(text, CommandSource.WIDGET)
        }
    }

    /**
     * BUG-B: lets the user abort a running turn from the Chat input row.
     * Mirrors `DashboardViewModel.stopTask()` — both funnel through
     * `CommandGateway.stopCurrent()` which flips the KillSwitch. The
     * orchestrator checks the flag inside its streaming + execution loops
     * and bails out, so the Stop button ends up as the canonical panic
     * brake for any in-flight command.
     */
    fun stopCommand() {
        commandGateway.stopCurrent()
    }

    fun toggleVoice() {
        when (voiceState.value) {
            VoiceState.LISTENING -> voiceCommandSource.stopListening()
            VoiceState.IDLE, VoiceState.ERROR -> voiceCommandSource.startListening()
            VoiceState.PROCESSING -> {}
        }
    }

    override fun onCleared() {
        voiceCommandSource.stopListening()
        super.onCleared()
    }
}
