package com.chibiclaw.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chibiclaw.perception.PermissionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 10 — backing view-model for [PermissionWizardScreen].
 *
 * Wraps [PermissionStatus] and auto-refreshes the snapshot every 1.5s
 * so when the user flips a toggle in Android Settings and returns to
 * the wizard, the checkmark updates automatically without needing a
 * manual "refresh" button.
 */
@HiltViewModel
class PermissionWizardViewModel @Inject constructor(
    private val permissionStatus: PermissionStatus
) : ViewModel() {

    private val _entries = MutableStateFlow<List<PermissionStatus.PermissionEntry>>(emptyList())
    val entries: StateFlow<List<PermissionStatus.PermissionEntry>> = _entries.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            while (true) {
                delay(1_500L)
                refresh()
            }
        }
    }

    fun refresh() {
        _entries.value = permissionStatus.snapshot()
    }

    fun missingCount(): Int = _entries.value.count { it.required && !it.granted }
    fun allRequiredGranted(): Boolean = missingCount() == 0
}
