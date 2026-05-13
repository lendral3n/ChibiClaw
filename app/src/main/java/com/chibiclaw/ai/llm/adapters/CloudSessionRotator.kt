package com.chibiclaw.ai.llm.adapters

import com.chibiclaw.ai.llm.session.ClaudeWebSession
import com.chibiclaw.ai.llm.session.GPTWebSession
import com.chibiclaw.data.prefs.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CloudSessionRotator — periodic ping ke mundane endpoint untuk verify session
 * masih valid. Dipanggil dari WorkManager periodic (Phase 4 sub-milestone)
 * atau on-demand sebelum LLM call.
 *
 * Return: true = session valid, false = expired / 401 → trigger re-login UI.
 */
@Singleton
class CloudSessionRotator @Inject constructor(
    private val securePreferences: SecurePreferences,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** Validate Claude session — ping /api/organizations, expect 200. */
    suspend fun validateClaude(): Boolean = mutex.withLock {
        val raw = securePreferences.getString(ClaudeWebAdapter.KEY_SESSION) ?: return false
        val session = runCatching { json.decodeFromString<ClaudeWebSession>(raw) }.getOrNull() ?: return false
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("https://claude.ai/api/organizations")
                    .header("Cookie", session.cookies.joinToString("; "))
                    .header("User-Agent", session.userAgent)
                    .header("anthropic-client-sha", session.clientSha)
                    .header("anthropic-client-version", session.clientVersion)
                    .header("anthropic-device-id", session.deviceId)
                    .get()
                    .build()
                http.newCall(req).execute().use { resp ->
                    val ok = resp.isSuccessful
                    if (!ok) Timber.w("Claude session validate HTTP ${resp.code}")
                    if (ok) {
                        val updated = session.copy(lastValidatedAtMs = System.currentTimeMillis())
                        securePreferences.putString(
                            ClaudeWebAdapter.KEY_SESSION,
                            json.encodeToString(ClaudeWebSession.serializer(), updated),
                        )
                    }
                    ok
                }
            } catch (t: Throwable) {
                Timber.w(t, "Claude validate exception")
                false
            }
        }
    }

    /** Validate GPT session — ping /api/auth/session. */
    suspend fun validateGPT(): Boolean = mutex.withLock {
        val raw = securePreferences.getString(GPTWebAdapter.KEY_SESSION) ?: return false
        val session = runCatching { json.decodeFromString<GPTWebSession>(raw) }.getOrNull() ?: return false
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("https://chatgpt.com/api/auth/session")
                    .header("Cookie", session.cookies.joinToString("; "))
                    .header("User-Agent", session.userAgent)
                    .header("Authorization", "Bearer ${session.accessToken}")
                    .get()
                    .build()
                http.newCall(req).execute().use { resp ->
                    val ok = resp.isSuccessful
                    if (!ok) Timber.w("GPT session validate HTTP ${resp.code}")
                    if (ok) {
                        val updated = session.copy(lastValidatedAtMs = System.currentTimeMillis())
                        securePreferences.putString(
                            GPTWebAdapter.KEY_SESSION,
                            json.encodeToString(GPTWebSession.serializer(), updated),
                        )
                    }
                    ok
                }
            } catch (t: Throwable) {
                Timber.w(t, "GPT validate exception")
                false
            }
        }
    }
}
