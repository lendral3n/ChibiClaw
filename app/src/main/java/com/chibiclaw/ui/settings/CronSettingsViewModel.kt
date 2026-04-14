package com.chibiclaw.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chibiclaw.gateway.source.CronSource
import com.chibiclaw.memory.local.entity.CronTaskEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel backing [CronSettingsScreen]. Wraps [CronSource] so the UI only
 * ever sees [CronTaskEntity] — the screen does not need to know about
 * WorkManager at all, the source handles the scheduling side of every edit.
 */
@HiltViewModel
class CronSettingsViewModel @Inject constructor(
    private val cronSource: CronSource
) : ViewModel() {

    val tasks: StateFlow<List<CronTaskEntity>> =
        cronSource.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addTask(command: String, intervalMinutes: Long) {
        if (command.isBlank()) return
        val clamped = intervalMinutes.coerceAtLeast(15L)
        val task = CronTaskEntity(
            id = UUID.randomUUID().toString(),
            command = command.trim(),
            intervalMinutes = clamped,
            enabled = true
        )
        viewModelScope.launch { cronSource.upsertTask(task) }
    }

    fun toggle(task: CronTaskEntity) {
        viewModelScope.launch {
            cronSource.upsertTask(task.copy(enabled = !task.enabled))
        }
    }

    fun delete(task: CronTaskEntity) {
        viewModelScope.launch { cronSource.deleteTask(task) }
    }
}
