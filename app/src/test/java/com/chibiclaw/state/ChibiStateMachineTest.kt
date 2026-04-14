package com.chibiclaw.state

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChibiStateMachineTest {

    private lateinit var stateMachine: ChibiStateMachine

    @Before
    fun setUp() {
        stateMachine = ChibiStateMachine()
    }

    @Test
    fun `initial state is IDLE`() {
        assertEquals(ChibiState.IDLE, stateMachine.current)
    }

    @Test
    fun `IDLE to PLANNING is valid`() {
        val result = stateMachine.transition(ChibiState.PLANNING)
        assertTrue(result)
        assertEquals(ChibiState.PLANNING, stateMachine.current)
    }

    @Test
    fun `PLANNING to EXECUTING is valid`() {
        stateMachine.transition(ChibiState.PLANNING)
        val result = stateMachine.transition(ChibiState.EXECUTING)
        assertTrue(result)
        assertEquals(ChibiState.EXECUTING, stateMachine.current)
    }

    @Test
    fun `reset returns to IDLE`() {
        stateMachine.transition(ChibiState.PLANNING)
        stateMachine.transition(ChibiState.EXECUTING)
        stateMachine.reset()
        assertEquals(ChibiState.IDLE, stateMachine.current)
    }

    @Test
    fun `setError transitions to ERROR_RECOVERY`() {
        stateMachine.transition(ChibiState.PLANNING)
        stateMachine.transition(ChibiState.EXECUTING)
        stateMachine.setError("test error")
        assertEquals(ChibiState.ERROR_RECOVERY, stateMachine.current)
    }
}
