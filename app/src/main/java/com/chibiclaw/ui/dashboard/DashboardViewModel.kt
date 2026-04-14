package com.chibiclaw.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chibiclaw.ai.EngineState
import com.chibiclaw.ai.GemmaEngineManager
import com.chibiclaw.core.ChibiOrchestrator
import com.chibiclaw.executor.StepRunner
import com.chibiclaw.executor.UndoStack
import com.chibiclaw.gateway.CommandGateway
import com.chibiclaw.memory.MemoryManager
import com.chibiclaw.memory.local.entity.CommandHistory
import com.chibiclaw.memory.pref.SecurePreferences
import com.chibiclaw.service.ChibiAccessibility
import com.chibiclaw.state.ChibiState
import com.chibiclaw.state.ChibiStateMachine
import com.chibiclaw.state.StateObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    stateObserver: StateObserver,
    private val memoryManager: MemoryManager,
    private val commandGateway: CommandGateway,
    private val stateMachine: ChibiStateMachine,
    private val engineManager: GemmaEngineManager,
    private val securePreferences: SecurePreferences,
    private val undoStack: UndoStack,
    orchestrator: ChibiOrchestrator
) : ViewModel() {

    val state: StateFlow<ChibiState> = stateObserver.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChibiState.IDLE)

    val lastError: StateFlow<String?> = stateObserver.lastError
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val engineState: StateFlow<EngineState> = engineManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EngineState.UNLOADED)

    private val _history = MutableStateFlow<List<CommandHistory>>(emptyList())
    val history: StateFlow<List<CommandHistory>> = _history

    /**
     * Rolling buffer of the latest 10 [StepRunner.StepLog] entries emitted by
     * the orchestrator during command processing. Dashboard renders this as a
     * mini timeline so the user can see real-time progress ("Approval → Fast
     * path → Executing → Done") while Fuu is working on a command.
     */
    private val _recentSteps = MutableStateFlow<List<StepRunner.StepLog>>(emptyList())
    val recentSteps: StateFlow<List<StepRunner.StepLog>> = _recentSteps

    /**
     * BUG-L: Dashboard surfaces whether the Accessibility service is
     * actually bound. `ChibiAccessibility.isConnected()` is a static
     * getter on the service singleton, so we poll it instead of
     * subscribing (AccessibilityService doesn't expose a LifecycleOwner).
     * A 2-second poll is cheap and close enough to real-time for an
     * indicator — when the user flips the OS toggle, they see the
     * indicator flip within 2 seconds.
     */
    private val _accessibilityConnected = MutableStateFlow(ChibiAccessibility.isConnected())
    val accessibilityConnected: StateFlow<Boolean> = _accessibilityConnected

    /**
     * Phase 10 — undo availability mirror. Dashboard polls UndoStack.size()
     * on the same 2s cadence so the Undo FAB can hide itself when there's
     * nothing left to roll back. We don't expose a StateFlow from UndoStack
     * itself because the stack is a thread-safe ArrayDeque, and wrapping
     * every push/pop in a Channel would be overkill for a UI that only
     * refreshes every 2s.
     */
    private val _undoCount = MutableStateFlow(0)
    val undoCount: StateFlow<Int> = _undoCount

    private val _undoToast = MutableStateFlow<String?>(null)
    val undoToast: StateFlow<String?> = _undoToast

    val modelFileName: String
        get() = securePreferences.getModelPath().substringAfterLast("/")

    val modelBackend: String
        get() = securePreferences.getModelBackend()

    init {
        loadHistory()
        viewModelScope.launch {
            orchestrator.stepLog.collect { step ->
                _recentSteps.value = (_recentSteps.value + step).takeLast(10)
            }
        }
        // BUG-L: cheap 2-second poll for the accessibility connection
        // and undo-stack size.
        viewModelScope.launch {
            while (true) {
                _accessibilityConnected.value = ChibiAccessibility.isConnected()
                _undoCount.value = undoStack.size()
                delay(2_000L)
            }
        }
    }

    fun stopTask() {
        commandGateway.stopCurrent()
        stateMachine.reset()
    }

    fun undoLast() {
        viewModelScope.launch {
            val result = undoStack.undoLast()
            _undoToast.value = result
            _undoCount.value = undoStack.size()
            // Auto-clear toast after a beat so the next undo shows fresh.
            delay(3_000L)
            _undoToast.value = null
        }
    }

    fun clearUndoToast() {
        _undoToast.value = null
    }

    fun refresh() = loadHistory()

    private fun loadHistory() {
        viewModelScope.launch {
            _history.value = memoryManager.getRecentCommands(20)
        }
    }
}
