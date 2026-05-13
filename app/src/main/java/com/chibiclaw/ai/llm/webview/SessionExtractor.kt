package com.chibiclaw.ai.llm.webview

import android.webkit.CookieManager
import com.chibiclaw.ai.llm.adapters.ClaudeWebAdapter
import com.chibiclaw.ai.llm.adapters.GPTWebAdapter
import com.chibiclaw.ai.llm.session.ClaudeWebSession
import com.chibiclaw.ai.llm.session.GPTWebSession
import com.chibiclaw.data.prefs.SecurePreferences
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SessionExtractor — extract session blob dari WebView post-login lalu
 * persist ke SecurePreferences.
 *
 * Format extracted (dipanggil dari `CloudLoginWebViewScreen` setelah JS
 * bridge listen URL_CHANGED → match success pattern):
 *
 *  ClaudeWebSession:
 *    - cookies: from CookieManager.getCookie("https://claude.ai")
 *    - clientSha, clientVersion: JS injection — window object atau header sniff
 *    - deviceId: localStorage.getItem('anthropic-device-id')
 *    - orgId, userId, activeConvId: dari JSON /api/organizations response
 *
 *  GPTWebSession:
 *    - cookies: CookieManager.getCookie("https://chatgpt.com")
 *    - accessToken: fetch('/api/auth/session') → {accessToken}
 *    - userId: dari /api/auth/session response user.id
 *
 * Phase 4 implementation: extractor accept pre-collected fields dari JS bridge
 * (CloudLoginWebViewScreen). Tidak melakukan JS injection sendiri di sini —
 * caller punya WebView dengan @JavascriptInterface yang feed data.
 */
@Singleton
class SessionExtractor @Inject constructor(
    private val securePreferences: SecurePreferences,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun extractAndPersistClaude(
        orgId: String,
        userId: String,
        activeConvId: String?,
        clientSha: String,
        clientVersion: String,
        deviceId: String,
        userAgent: String,
    ): Boolean {
        val cookies = CookieManager.getInstance().getCookie("https://claude.ai")
            ?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() }
            .orEmpty()
        if (cookies.isEmpty() || clientSha.isBlank() || orgId.isBlank()) {
            Timber.w("Claude session extract incomplete (cookies=${cookies.size}, sha='${clientSha.take(8)}', org='$orgId')")
            return false
        }
        val now = System.currentTimeMillis()
        val session = ClaudeWebSession(
            orgId = orgId,
            userId = userId,
            activeConvId = activeConvId,
            cookies = cookies,
            clientSha = clientSha,
            clientVersion = clientVersion,
            deviceId = deviceId,
            userAgent = userAgent,
            createdAtMs = now,
            lastValidatedAtMs = now,
        )
        securePreferences.putString(ClaudeWebAdapter.KEY_SESSION, json.encodeToString(ClaudeWebSession.serializer(), session))
        Timber.i("Claude session persisted (org=$orgId, user=$userId, cookies=${cookies.size})")
        return true
    }

    fun extractAndPersistGPT(
        userId: String,
        accessToken: String,
        conversationId: String?,
        userAgent: String,
    ): Boolean {
        val cookies = CookieManager.getInstance().getCookie("https://chatgpt.com")
            ?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() }
            .orEmpty()
        if (cookies.isEmpty() || accessToken.isBlank() || userId.isBlank()) {
            Timber.w("GPT session extract incomplete (cookies=${cookies.size}, token='${accessToken.take(8)}', user='$userId')")
            return false
        }
        val now = System.currentTimeMillis()
        val session = GPTWebSession(
            userId = userId,
            accessToken = accessToken,
            cookies = cookies,
            conversationId = conversationId,
            userAgent = userAgent,
            createdAtMs = now,
            lastValidatedAtMs = now,
        )
        securePreferences.putString(GPTWebAdapter.KEY_SESSION, json.encodeToString(GPTWebSession.serializer(), session))
        Timber.i("GPT session persisted (user=$userId, cookies=${cookies.size})")
        return true
    }

    fun clearClaudeSession() {
        securePreferences.putString(ClaudeWebAdapter.KEY_SESSION, null)
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
        }
    }

    fun clearGPTSession() {
        securePreferences.putString(GPTWebAdapter.KEY_SESSION, null)
    }
}
