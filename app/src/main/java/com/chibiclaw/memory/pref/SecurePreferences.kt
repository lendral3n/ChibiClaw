package com.chibiclaw.memory.pref

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "chibi_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun setModelPath(path: String) = prefs.edit().putString(KEY_MODEL_PATH, path).apply()
    fun getModelPath(): String = prefs.getString(KEY_MODEL_PATH, DEFAULT_MODEL_PATH) ?: DEFAULT_MODEL_PATH

    // E2B is the lightweight text-only model for simple commands. Optional —
    // if unset, the router falls back to the E4B path for everything.
    //
    // NOTE: With the ModelLibrary refactor the library itself is the source of
    // truth, but we keep these two setters/getters for back-compat so the rest
    // of the codebase (auto-load, AIDL, etc.) keeps compiling until every
    // caller is migrated.
    fun setModelPathE2B(path: String) = prefs.edit().putString(KEY_MODEL_PATH_E2B, path).apply()
    fun getModelPathE2B(): String = prefs.getString(KEY_MODEL_PATH_E2B, "") ?: ""

    /**
     * Persisted JSON blob representing the user's [ModelLibrary]. Stored as a
     * JSONArray-of-objects so we can round-trip entries without a DB migration.
     * Empty string = no models uploaded yet.
     */
    fun setModelLibraryJson(json: String) =
        prefs.edit().putString(KEY_MODEL_LIBRARY_JSON, json).apply()
    fun getModelLibraryJson(): String =
        prefs.getString(KEY_MODEL_LIBRARY_JSON, "") ?: ""

    /** The id of the currently-selected model from [ModelLibrary]. */
    fun setActiveModelId(id: String) =
        prefs.edit().putString(KEY_ACTIVE_MODEL_ID, id).apply()
    fun getActiveModelId(): String =
        prefs.getString(KEY_ACTIVE_MODEL_ID, "") ?: ""

    fun setModelBackend(backend: String) = prefs.edit().putString(KEY_MODEL_BACKEND, backend).apply()
    fun getModelBackend(): String = prefs.getString(KEY_MODEL_BACKEND, "GPU") ?: "GPU"

    fun setSetupComplete(done: Boolean) = prefs.edit().putBoolean(KEY_SETUP_DONE, done).apply()
    fun isSetupComplete(): Boolean = prefs.getBoolean(KEY_SETUP_DONE, false)

    fun setCallerWhitelist(packages: Set<String>) =
        prefs.edit().putStringSet(KEY_CALLER_WHITELIST, packages).apply()
    fun getCallerWhitelist(): Set<String> =
        prefs.getStringSet(KEY_CALLER_WHITELIST, emptySet()) ?: emptySet()

    // Persona settings
    fun setPersonaPrompt(prompt: String) = prefs.edit().putString(KEY_PERSONA_PROMPT, prompt).apply()
    fun getPersonaPrompt(): String? = prefs.getString(KEY_PERSONA_PROMPT, null)

    fun setPersonaLanguage(lang: String) = prefs.edit().putString(KEY_PERSONA_LANGUAGE, lang).apply()
    fun getPersonaLanguage(): String? = prefs.getString(KEY_PERSONA_LANGUAGE, null)

    fun setPersonaTone(tone: String) = prefs.edit().putString(KEY_PERSONA_TONE, tone).apply()
    fun getPersonaTone(): String? = prefs.getString(KEY_PERSONA_TONE, null)

    fun setDevMode(enabled: Boolean) = prefs.edit().putBoolean(KEY_DEV_MODE, enabled).apply()
    fun isDevMode(): Boolean = prefs.getBoolean(KEY_DEV_MODE, false)

    // Notification trigger settings — editable from Settings → Notification Triggers
    fun setNotificationWhitelist(packages: Set<String>) =
        prefs.edit().putStringSet(KEY_NOTIF_WHITELIST, packages).apply()
    fun getNotificationWhitelist(): Set<String> =
        prefs.getStringSet(KEY_NOTIF_WHITELIST, DEFAULT_NOTIF_WHITELIST) ?: DEFAULT_NOTIF_WHITELIST

    fun setNotificationKeywords(keywords: Set<String>) =
        prefs.edit().putStringSet(KEY_NOTIF_KEYWORDS, keywords).apply()
    fun getNotificationKeywords(): Set<String> =
        prefs.getStringSet(KEY_NOTIF_KEYWORDS, DEFAULT_NOTIF_KEYWORDS) ?: DEFAULT_NOTIF_KEYWORDS

    // ─── Auto-Control per-app settings ────────────────────────────────────
    // Persisted as a JSON array — see [AutoControlConfig.listToJson]. Every
    // entry represents one package the user has explicitly configured.
    // Packages without an entry fall back to [AutoControlConfig.default].
    fun setAutoControlConfigJson(json: String) =
        prefs.edit().putString(KEY_AUTO_CONTROL_JSON, json).apply()
    fun getAutoControlConfigJson(): String =
        prefs.getString(KEY_AUTO_CONTROL_JSON, "") ?: ""

    companion object {
        private const val KEY_MODEL_PATH = "model_path"          // legacy — E4B primary
        private const val KEY_MODEL_PATH_E2B = "model_path_e2b"  // legacy — E2B optional
        private const val KEY_MODEL_LIBRARY_JSON = "model_library_json"
        private const val KEY_ACTIVE_MODEL_ID = "active_model_id"
        private const val KEY_MODEL_BACKEND = "model_backend"
        private const val KEY_SETUP_DONE = "setup_done"
        private const val KEY_CALLER_WHITELIST = "caller_whitelist"
        private const val KEY_PERSONA_PROMPT = "persona_prompt"
        private const val KEY_PERSONA_LANGUAGE = "persona_language"
        private const val KEY_PERSONA_TONE = "persona_tone"
        private const val KEY_DEV_MODE = "dev_mode"
        private const val KEY_NOTIF_WHITELIST = "notif_whitelist"
        private const val KEY_NOTIF_KEYWORDS = "notif_keywords"
        private const val KEY_AUTO_CONTROL_JSON = "auto_control_json"

        val DEFAULT_NOTIF_WHITELIST = setOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.google.android.gm"
        )
        val DEFAULT_NOTIF_KEYWORDS = setOf(
            "balas", "reply", "jawab", "kirim", "send",
            "@fuu", "fuu tolong", "fuu help"
        )

        const val DEFAULT_MODEL_PATH =
            "/storage/emulated/0/Download/Gemma4/gemma4_4b_v09_obfus_fix_all_modalities_thinking.litertlm"
    }
}
