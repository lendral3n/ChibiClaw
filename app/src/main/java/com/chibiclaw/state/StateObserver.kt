package com.chibiclaw.state

import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StateObserver @Inject constructor(
    private val stateMachine: ChibiStateMachine
) {
    val state: StateFlow<ChibiState> = stateMachine.state
    val lastError: StateFlow<String?> = stateMachine.lastError
}
