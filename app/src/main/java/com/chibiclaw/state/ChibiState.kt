package com.chibiclaw.state

enum class ChibiState {
    IDLE,
    PLANNING,
    EXECUTING,
    VERIFYING,
    ERROR_RECOVERY,
    WAITING_USER,
    PAUSED,
    COMPLETED;

    fun canTransitionTo(next: ChibiState): Boolean = when (this) {
        IDLE -> next in setOf(PLANNING)
        PLANNING -> next in setOf(EXECUTING, WAITING_USER, ERROR_RECOVERY, IDLE)
        EXECUTING -> next in setOf(VERIFYING, WAITING_USER, ERROR_RECOVERY, PAUSED, IDLE)
        VERIFYING -> next in setOf(EXECUTING, COMPLETED, ERROR_RECOVERY, IDLE)
        ERROR_RECOVERY -> next in setOf(PLANNING, EXECUTING, WAITING_USER, IDLE)
        WAITING_USER -> next in setOf(EXECUTING, PLANNING, IDLE)
        PAUSED -> next in setOf(EXECUTING, IDLE)
        COMPLETED -> next in setOf(IDLE)
    }
}
