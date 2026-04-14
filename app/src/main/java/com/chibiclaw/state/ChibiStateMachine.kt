package com.chibiclaw.state

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChibiStateMachine @Inject constructor() {

    private val _state = MutableStateFlow(ChibiState.IDLE)
    val state: StateFlow<ChibiState> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    val current: ChibiState get() = _state.value

    fun transition(next: ChibiState, reason: String = ""): Boolean {
        val current = _state.value
        return if (current.canTransitionTo(next)) {
            Log.d(TAG, "State: $current → $next${if (reason.isNotEmpty()) " ($reason)" else ""}")
            _state.value = next
            if (next != ChibiState.ERROR_RECOVERY) _lastError.value = null
            true
        } else {
            Log.w(TAG, "Invalid transition: $current → $next")
            false
        }
    }

    fun setError(error: String) {
        _lastError.value = error
        transition(ChibiState.ERROR_RECOVERY, error)
    }

    fun reset() {
        _lastError.value = null
        _state.value = ChibiState.IDLE
    }

    companion object {
        private const val TAG = "ChibiStateMachine"
    }
}
