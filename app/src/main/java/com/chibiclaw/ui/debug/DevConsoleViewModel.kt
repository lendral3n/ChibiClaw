package com.chibiclaw.ui.debug

import androidx.lifecycle.ViewModel
import com.chibiclaw.ai.GemmaEngineManager
import com.chibiclaw.debug.DevLog
import com.chibiclaw.debug.DevLogger
import com.chibiclaw.state.ChibiStateMachine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class DevConsoleViewModel @Inject constructor(
    private val devLogger: DevLogger,
    val engineManager: GemmaEngineManager,
    val stateMachine: ChibiStateMachine
) : ViewModel() {

    val logs: StateFlow<List<DevLog>> = devLogger.logs

    val engineState = engineManager.state
    val agentState = stateMachine.state

    fun clearLogs() = devLogger.clear()

    fun exportLogs(): String = devLogger.export()
}
