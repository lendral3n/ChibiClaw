package com.chibiclaw.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chibiclaw.memory.pref.SecurePreferences
import com.chibiclaw.util.InstalledAppsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [NotificationSettingsScreen]. Holds two editable string sets —
 * whitelisted package names and trigger keywords — and persists every
 * change through [SecurePreferences]. [com.chibiclaw.gateway.source.NotificationSource]
 * reads those same keys on each notification so edits take effect
 * immediately.
 *
 * In addition to raw package names we expose [whitelistLabeled] — a map of
 * packageName → human label resolved via [InstalledAppsProvider] — so the
 * UI can show "WhatsApp" instead of "com.whatsapp". The map is recomputed
 * every time the whitelist changes.
 */
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val installedAppsProvider: InstalledAppsProvider
) : ViewModel() {

    private val _whitelist = MutableStateFlow(securePreferences.getNotificationWhitelist())
    val whitelist: StateFlow<Set<String>> = _whitelist

    private val _whitelistLabeled = MutableStateFlow<Map<String, String>>(emptyMap())
    val whitelistLabeled: StateFlow<Map<String, String>> = _whitelistLabeled

    private val _keywords = MutableStateFlow(securePreferences.getNotificationKeywords())
    val keywords: StateFlow<Set<String>> = _keywords

    init {
        refreshLabels()
    }

    /** Re-resolves every whitelisted package to its human label. */
    private fun refreshLabels() {
        viewModelScope.launch {
            val labels = _whitelist.value.associateWith {
                installedAppsProvider.labelForPackage(it)
            }
            _whitelistLabeled.value = labels
        }
    }

    /**
     * Picker-friendly setter that replaces the entire whitelist in one
     * shot. Used by the "Pilih App" dialog which returns the full
     * selection set.
     */
    fun setWhitelist(packages: Set<String>) {
        _whitelist.value = packages
        securePreferences.setNotificationWhitelist(packages)
        refreshLabels()
    }

    fun addPackage(pkg: String) {
        val trimmed = pkg.trim()
        if (trimmed.isBlank()) return
        val updated = _whitelist.value + trimmed
        _whitelist.value = updated
        securePreferences.setNotificationWhitelist(updated)
        refreshLabels()
    }

    fun removePackage(pkg: String) {
        val updated = _whitelist.value - pkg
        _whitelist.value = updated
        securePreferences.setNotificationWhitelist(updated)
        refreshLabels()
    }

    fun addKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return
        val updated = _keywords.value + trimmed
        _keywords.value = updated
        securePreferences.setNotificationKeywords(updated)
    }

    fun removeKeyword(keyword: String) {
        val updated = _keywords.value - keyword
        _keywords.value = updated
        securePreferences.setNotificationKeywords(updated)
    }

    fun resetDefaults() {
        val defaultWl = SecurePreferences.DEFAULT_NOTIF_WHITELIST
        val defaultKw = SecurePreferences.DEFAULT_NOTIF_KEYWORDS
        _whitelist.value = defaultWl
        _keywords.value = defaultKw
        securePreferences.setNotificationWhitelist(defaultWl)
        securePreferences.setNotificationKeywords(defaultKw)
        refreshLabels()
    }
}
