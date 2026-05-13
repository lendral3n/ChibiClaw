package com.chibiclaw.data.prefs

import androidx.security.crypto.EncryptedSharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wrapper EncryptedSharedPreferences. Semua secret + consent flag di sini.
 *
 * Kategori key:
 * - Setup status: setup_complete, privacy_notice_accepted, vendor_wizard_done
 * - Consent per permission: consent_overlay, consent_microphone, consent_notification, ...
 * - Audit config: audit_retention_days
 * - Cloud session (Phase 4+): claude_session_json, gpt_session_json, gemini_api_key
 * - ElevenLabs (Phase 2+): elevenlabs_api_key
 *
 * Pakai StateFlow untuk reactive UI subscription.
 */
class SecurePreferences(private val prefs: EncryptedSharedPreferences) {

    // ===== Setup flow status =====

    private val _setupComplete = MutableStateFlow(isSetupComplete())
    val setupComplete: StateFlow<Boolean> = _setupComplete.asStateFlow()

    fun isSetupComplete(): Boolean = prefs.getBoolean(KEY_SETUP_COMPLETE, false)

    fun setSetupComplete(value: Boolean) {
        prefs.edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()
        _setupComplete.value = value
    }

    fun isPrivacyAccepted(): Boolean = prefs.getBoolean(KEY_PRIVACY_ACCEPTED, false)
    fun setPrivacyAccepted(value: Boolean) {
        prefs.edit().putBoolean(KEY_PRIVACY_ACCEPTED, value).apply()
    }

    fun isVendorWizardDone(): Boolean = prefs.getBoolean(KEY_VENDOR_WIZARD_DONE, false)
    fun setVendorWizardDone(value: Boolean) {
        prefs.edit().putBoolean(KEY_VENDOR_WIZARD_DONE, value).apply()
    }

    // ===== Granular per-permission consent =====

    fun consent(permission: ConsentKey): Boolean =
        prefs.getBoolean(consentKey(permission), false)

    fun setConsent(permission: ConsentKey, value: Boolean) {
        prefs.edit().putBoolean(consentKey(permission), value).apply()
    }

    /** Snapshot semua consent state — untuk audit log entry. */
    fun currentConsentSnapshot(): String {
        return ConsentKey.entries.joinToString(",") { key ->
            "${key.name}=${consent(key)}"
        }
    }

    // ===== Audit config =====

    fun auditRetentionDays(): Int = prefs.getInt(KEY_AUDIT_RETENTION_DAYS, DEFAULT_AUDIT_RETENTION_DAYS)
    fun setAuditRetentionDays(days: Int) {
        prefs.edit().putInt(KEY_AUDIT_RETENTION_DAYS, days.coerceIn(30, 365)).apply()
    }

    // ===== Generic accessors (untuk extensibility Phase 1+) =====

    fun getString(key: String): String? = prefs.getString(key, null)
    fun putString(key: String, value: String?) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)
    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getInt(key: String, default: Int = 0): Int = prefs.getInt(key, default)
    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    // ===== Erase all =====

    /** Erase semua SecurePreferences. Setelah ini SQLCipher passphrase hilang → DB irrecoverable. */
    fun clearAll() {
        prefs.edit().clear().apply()
        _setupComplete.value = false
    }

    private fun consentKey(permission: ConsentKey): String = "consent_${permission.name.lowercase()}"

    companion object {
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_PRIVACY_ACCEPTED = "privacy_notice_accepted"
        private const val KEY_VENDOR_WIZARD_DONE = "vendor_wizard_done"
        private const val KEY_AUDIT_RETENTION_DAYS = "audit_retention_days"
        private const val DEFAULT_AUDIT_RETENTION_DAYS = 90
    }
}

/**
 * Permission yang user consent granular. Tidak semua di-grant Phase 0 — beberapa
 * dipakai di phase berikutnya tapi key sudah disiapkan supaya forward-compatible.
 */
enum class ConsentKey {
    OVERLAY,                // Phase 0
    NOTIFICATION_POST,      // Phase 0
    MICROPHONE,             // Phase 2
    ACCESSIBILITY,          // Phase 3
    SHIZUKU,                // Phase 3
    SMS,                    // Phase 3
    CONTACTS,               // Phase 3
    MEDIA_PROJECTION,       // Phase 5
    LOCATION,               // Phase 5/6
    CALENDAR,               // Phase 5/6
    NOTIFICATION_LISTENER,  // Phase 6
    USAGE_STATS,            // Phase 6
    CLOUD_LLM,              // Phase 4
    VOICE_BIOMETRIC,        // Phase 2 (ElevenLabs voice clone)
}
