package com.chibiclaw.ui.mode

import androidx.lifecycle.ViewModel
import com.chibiclaw.debug.DevModeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ModeSelectionViewModel @Inject constructor(
    private val devModeManager: DevModeManager
) : ViewModel() {

    fun verifyPin(pin: String): Boolean = devModeManager.verifyPin(pin)
}
