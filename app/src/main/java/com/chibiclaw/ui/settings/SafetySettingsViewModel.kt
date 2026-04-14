package com.chibiclaw.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chibiclaw.memory.MemoryManager
import com.chibiclaw.memory.local.entity.CommandHistory
import com.chibiclaw.memory.pref.SecurePreferences
import com.chibiclaw.safety.AutoControlConfig
import com.chibiclaw.safety.AutoControlRepository
import com.chibiclaw.util.InstalledAppsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SafetySettingsViewModel @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val memoryManager: MemoryManager,
    private val autoControlRepository: AutoControlRepository,
    private val installedAppsProvider: InstalledAppsProvider
) : ViewModel() {

    private val _whitelist = MutableStateFlow<Set<String>>(emptySet())
    val whitelist: StateFlow<Set<String>> = _whitelist

    private val _executionLog = MutableStateFlow<List<CommandHistory>>(emptyList())
    val executionLog: StateFlow<List<CommandHistory>> = _executionLog

    private val _logFilter = MutableStateFlow("ALL")
    val logFilter: StateFlow<String> = _logFilter

    /** Auto-Control configs (reactive — updates when repo changes). */
    val autoControlConfigs: StateFlow<List<AutoControlConfig>> =
        autoControlRepository.configs

    /** Pretty labels for every configured auto-control package. */
    private val _autoControlLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val autoControlLabels: StateFlow<Map<String, String>> = _autoControlLabels

    init {
        _whitelist.value = securePreferences.getCallerWhitelist()
        loadLog()
        refreshAutoControlLabels()
        // Keep label map in sync every time the config list changes. We
        // don't need a full Flow.collect — a simple viewModelScope launch
        // that re-reads on each upsert is enough because upserts happen
        // interactively on the main thread.
        viewModelScope.launch {
            autoControlRepository.configs.collect { refreshAutoControlLabels() }
        }
    }

    fun addToWhitelist(packageName: String) {
        val updated = _whitelist.value + packageName
        _whitelist.value = updated
        securePreferences.setCallerWhitelist(updated)
    }

    fun removeFromWhitelist(packageName: String) {
        val updated = _whitelist.value - packageName
        _whitelist.value = updated
        securePreferences.setCallerWhitelist(updated)
    }

    fun setLogFilter(filter: String) {
        _logFilter.value = filter
        loadLog()
    }

    private fun loadLog() {
        viewModelScope.launch {
            val all = memoryManager.getRecentCommands(50)
            _executionLog.value = when (_logFilter.value) {
                "SUCCESS" -> all.filter { it.state == "COMPLETED" }
                "FAILED" -> all.filter { it.state == "ERROR" }
                "BLOCKED" -> all.filter { it.state == "BLOCKED" }
                else -> all
            }
        }
    }

    // ─── Auto-Control ────────────────────────────────────────────────────
    fun addAutoControl(packageName: String) {
        if (autoControlRepository.get(packageName) != null) return
        autoControlRepository.upsert(AutoControlConfig.default(packageName))
    }

    fun toggleForeground(packageName: String, enabled: Boolean) {
        val cfg = autoControlRepository.getOrDefault(packageName)
        autoControlRepository.upsert(cfg.copy(foregroundEnabled = enabled))
    }

    fun toggleBackground(packageName: String, enabled: Boolean) {
        val cfg = autoControlRepository.getOrDefault(packageName)
        autoControlRepository.upsert(cfg.copy(backgroundEnabled = enabled))
    }

    fun toggleAction(packageName: String, action: String, enabled: Boolean) {
        val cfg = autoControlRepository.getOrDefault(packageName)
        // Empty set means "all allowed". As soon as the user flips one
        // action off we materialise the whitelist with every KNOWN action
        // minus the one they disabled — that way subsequent adds/removes
        // behave predictably instead of silently re-enabling everything.
        val base: Set<String> = if (cfg.allowedActions.isEmpty()) ALL_ACTIONS else cfg.allowedActions
        val updated = if (enabled) base + action else base - action
        autoControlRepository.upsert(cfg.copy(allowedActions = updated))
    }

    fun removeAutoControl(packageName: String) {
        autoControlRepository.remove(packageName)
    }

    private fun refreshAutoControlLabels() {
        viewModelScope.launch {
            val current = autoControlRepository.configs.value
            val labels = current.associate {
                it.packageName to installedAppsProvider.labelForPackage(it.packageName)
            }
            _autoControlLabels.value = labels
        }
    }

    companion object {
        val ALL_ACTIONS: Set<String> = setOf("launch", "tap", "type", "scroll", "back", "intent")
    }
}
