package com.chibiclaw.safety

import com.chibiclaw.memory.pref.SecurePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for per-package [AutoControlConfig]. Persists to
 * [SecurePreferences] as a JSON array and publishes the full list as a
 * [StateFlow] so UIs (SafetySettingsScreen) and consumers (AutoControlGate)
 * stay in sync without manual plumbing.
 *
 * Packages that have no entry yet are NOT auto-inserted — lookups fall
 * through to [AutoControlConfig.default]. That way the whitelist stays
 * small and only reflects apps the user has actually configured.
 */
@Singleton
class AutoControlRepository @Inject constructor(
    private val securePreferences: SecurePreferences
) {
    private val _configs = MutableStateFlow<List<AutoControlConfig>>(emptyList())
    val configs: StateFlow<List<AutoControlConfig>> = _configs.asStateFlow()

    init {
        _configs.value = AutoControlConfig.listFromJson(
            securePreferences.getAutoControlConfigJson()
        )
    }

    /**
     * Returns the stored entry for [packageName] or the global default if
     * the user has never configured it. Never returns null — that's the
     * whole point of having a fallback.
     */
    fun getOrDefault(packageName: String): AutoControlConfig {
        return _configs.value.firstOrNull { it.packageName == packageName }
            ?: AutoControlConfig.default(packageName)
    }

    /** Returns the stored entry or null if none has been saved yet. */
    fun get(packageName: String): AutoControlConfig? {
        return _configs.value.firstOrNull { it.packageName == packageName }
    }

    /**
     * Inserts or replaces the entry for [config.packageName] and persists
     * the full list. Writes happen on the caller thread — this is just a
     * JSON serialize which is cheap.
     */
    fun upsert(config: AutoControlConfig) {
        val updated = _configs.value
            .filterNot { it.packageName == config.packageName } + config
        _configs.value = updated.sortedBy { it.packageName }
        persist()
    }

    fun remove(packageName: String) {
        val updated = _configs.value.filterNot { it.packageName == packageName }
        if (updated.size == _configs.value.size) return
        _configs.value = updated
        persist()
    }

    fun clearAll() {
        _configs.value = emptyList()
        persist()
    }

    private fun persist() {
        securePreferences.setAutoControlConfigJson(
            AutoControlConfig.listToJson(_configs.value)
        )
    }
}
