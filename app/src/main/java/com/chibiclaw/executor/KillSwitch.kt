package com.chibiclaw.executor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KillSwitch @Inject constructor() {
    private val _activated = MutableStateFlow(false)
    val activated: StateFlow<Boolean> = _activated.asStateFlow()

    fun activate() { _activated.value = true }
    fun reset() { _activated.value = false }
    fun isActive(): Boolean = _activated.value
}
