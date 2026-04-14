package com.chibiclaw.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DevModeManager @Inject constructor() {

    private val _isDevMode = MutableStateFlow(false)
    val isDevMode: StateFlow<Boolean> = _isDevMode.asStateFlow()

    val isActive: Boolean get() = _isDevMode.value

    fun unlock(): Boolean {
        _isDevMode.value = true
        return true
    }

    fun lock() {
        _isDevMode.value = false
    }

    fun verifyPin(pin: String): Boolean {
        return if (pin == DEV_PIN) {
            unlock()
            true
        } else false
    }

    companion object {
        private const val DEV_PIN = "9344"
    }
}
