package com.chibiclaw.executor

import android.util.Log
import com.chibiclaw.state.ChibiState
import com.chibiclaw.state.ChibiStateMachine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P4.3 — StepRunner
 * Manages step-by-step execution tracking, separated from ChibiOrchestrator.
 * Emits StepLog events for real-time UI updates (Dashboard, FloatingOverlay).
 */
@Singleton
class StepRunner @Inject constructor(
    private val stateMachine: ChibiStateMachine
) {
    data class StepLog(
        val stepNumber: Int,
        val description: String,
        val state: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _stepLog = MutableSharedFlow<StepLog>(extraBufferCapacity = 32)
    val stepLog: SharedFlow<StepLog> = _stepLog.asSharedFlow()

    private var currentStep = 0

    fun reset() {
        currentStep = 0
    }

    fun startStep(description: String) {
        currentStep++
        val log = StepLog(
            stepNumber = currentStep,
            description = description,
            state = "RUNNING"
        )
        _stepLog.tryEmit(log)
        Log.d(TAG, "Step $currentStep: $description")
    }

    fun completeStep(description: String, success: Boolean = true) {
        val state = if (success) "DONE" else "FAILED"
        val log = StepLog(
            stepNumber = currentStep,
            description = description,
            state = state
        )
        _stepLog.tryEmit(log)
        Log.d(TAG, "Step $currentStep [$state]: $description")
    }

    fun reportError(error: String) {
        val log = StepLog(
            stepNumber = currentStep,
            description = error,
            state = "ERROR"
        )
        _stepLog.tryEmit(log)
        Log.e(TAG, "Step $currentStep ERROR: $error")
    }

    companion object {
        private const val TAG = "StepRunner"
    }
}
