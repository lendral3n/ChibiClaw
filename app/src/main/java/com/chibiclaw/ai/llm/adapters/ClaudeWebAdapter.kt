package com.chibiclaw.ai.llm.adapters

import com.chibiclaw.ai.llm.AdapterCapability
import com.chibiclaw.ai.llm.AdapterErrorClass
import com.chibiclaw.ai.llm.AdapterQuotaTracker
import com.chibiclaw.ai.llm.AgentPrompt
import com.chibiclaw.ai.llm.InferenceAdapter
import com.chibiclaw.ai.llm.InferenceChunk
import com.chibiclaw.ai.llm.InferenceResult
import com.chibiclaw.ai.llm.PromptBuilder
import com.chibiclaw.ai.llm.session.ClaudeWebSession
import com.chibiclaw.data.prefs.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ClaudeWebAdapter — reverse-engineered claude.ai web session.
 *
 * Session source: hasil WebView login (Phase 4 W2.1) → SessionExtractor →
 * SecurePreferences `claude_session_json`. Saat adapter dipanggil, parse JSON
 * → set cookie header + anthropic-client-* headers.
 *
 * Endpoint: `https://claude.ai/api/organizations/{orgId}/chat_conversations/{convId}/completion`
 *   POST with SSE response stream.
 *
 * Phase 4 implementation: non-streaming aggregate (collect all SSE chunk → return
 * combined). Phase 4 sub-milestone: streaming proper via Flow emit per chunk.
 *
 * Rate limit policy: 1 call / 30s minimal (avoid Anthropic suspicious-activity
 * flag). Enforced di AgentRuntime caller; tidak di-enforce di sini supaya
 * router bisa fallback cepat.
 */
@Singleton
class ClaudeWebAdapter @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val quotaTracker: AdapterQuotaTracker,
) : InferenceAdapter {

    override val id = "claude_web"
    override val capability = AdapterCapability(
        displayName = "Claude.ai (Web Session)",
        contextWindow = 200_000,
        supportsToolCalling = true,
        supportsStreaming = true,
        supportsVision = true,
        supportsConstrainedDecoding = false,
        isLocal = false,
        estimatedTpsDecode = 80f,
        estimatedTpsPrefill = 1500f,
        requiresAuth = true,
    )

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun isAvailable(): Boolean {
        val session = loadSession() ?: return false
        if (isSessionExpired(session)) return false
        quotaTracker.ensureConfig(id, capability.displayName, dailyQuota = -1)
        return quotaTracker.hasQuota(id)
    }

    override suspend fun complete(prompt: AgentPrompt): InferenceResult = withContext(Dispatchers.IO) {
        val session = loadSession()
            ?: return@withContext InferenceResult.Error(
                AdapterErrorClass.AUTH_EXPIRED,
                "Belum login Claude.ai. Setup wizard → Cloud → Claude login.",
            )
        if (isSessionExpired(session)) {
            return@withContext InferenceResult.Error(
                AdapterErrorClass.AUTH_EXPIRED,
                "Session Claude.ai expired (lebih dari ${session.maxAgeDays} hari). Re-login.",
            )
        }
        val convId = session.activeConvId ?: return@withContext InferenceResult.Error(
            AdapterErrorClass.AUTH_EXPIRED,
            "Conversation ID claude.ai belum di-prebuild. Re-login flow akan create.",
        )

        val url = "https://claude.ai/api/organizations/${session.orgId}/chat_conversations/$convId/completion"
        val body = buildJsonObject {
            put("prompt", PromptBuilder.toGemmaFormat(prompt))
            put("rendering_mode", "raw")
            put("timezone", java.time.ZoneId.systemDefault().id)
            put("attachments", buildJsonArray { })
            put("files", buildJsonArray { })
            put("personalized_styles", buildJsonArray {
                add(buildJsonObject { put("key", "Default") })
            })
        }

        val req = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(MEDIA_JSON))
            .header("User-Agent", session.userAgent)
            .header("Cookie", session.cookies.joinToString("; "))
            .header("anthropic-client-sha", session.clientSha)
            .header("anthropic-client-version", session.clientVersion)
            .header("anthropic-device-id", session.deviceId)
            .header("Accept", "text/event-stream")
            .header("Origin", "https://claude.ai")
            .header("Referer", "https://claude.ai/chats/$convId")
            .build()

        val start = System.currentTimeMillis()
        try {
            http.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use mapHttp(resp.code, raw)
                }
                val text = parseSse(raw)
                if (text.isBlank()) {
                    quotaTracker.recordError(id)
                    return@use InferenceResult.Error(
                        AdapterErrorClass.MODEL_ERROR,
                        "Claude SSE kosong: ${raw.take(400)}",
                    )
                }
                quotaTracker.recordSuccess(id)
                val latency = System.currentTimeMillis() - start
                Timber.d("Claude web ok ${text.length}c / ${latency}ms")
                InferenceResult.Success(
                    raw = text,
                    tokensUsed = text.length / 4,
                    latencyMs = latency,
                )
            }
        } catch (io: IOException) {
            quotaTracker.recordError(id)
            InferenceResult.Error(AdapterErrorClass.NETWORK, io.message ?: "IOException")
        } catch (t: Throwable) {
            quotaTracker.recordError(id)
            Timber.e(t, "Claude exception")
            InferenceResult.Error(AdapterErrorClass.UNKNOWN, t.message ?: t.javaClass.simpleName)
        }
    }

    override fun stream(prompt: AgentPrompt): Flow<InferenceChunk> {
        return flowOf(InferenceChunk(text = "", isLast = true))
    }

    override suspend fun shutdown() = Unit

    private fun loadSession(): ClaudeWebSession? {
        val raw = securePreferences.getString(KEY_SESSION) ?: return null
        return runCatching { json.decodeFromString<ClaudeWebSession>(raw) }.getOrNull()
    }

    private fun isSessionExpired(session: ClaudeWebSession): Boolean {
        val ageDays = (System.currentTimeMillis() - session.createdAtMs) / (1000L * 60 * 60 * 24)
        return ageDays >= session.maxAgeDays
    }

    /**
     * Parse SSE stream — Claude emit "event: completion\ndata: {...}\n\n" frames.
     * Aggregate semua delta `completion` ke single string.
     */
    private fun parseSse(raw: String): String {
        val sb = StringBuilder()
        raw.split("\n\n").forEach { frame ->
            val dataLine = frame.lineSequence().firstOrNull { it.startsWith("data:") } ?: return@forEach
            val payload = dataLine.removePrefix("data:").trim()
            if (payload == "[DONE]") return@forEach
            runCatching {
                val obj = json.parseToJsonElement(payload)
                val completion = obj.toString()
                    .substringAfter("\"completion\":\"", "")
                    .substringBefore("\"")
                if (completion.isNotEmpty()) {
                    sb.append(completion.replace("\\n", "\n").replace("\\\"", "\""))
                }
            }
        }
        return sb.toString()
    }

    private suspend fun mapHttp(code: Int, raw: String): InferenceResult.Error {
        val errClass = when (code) {
            401, 403 -> AdapterErrorClass.AUTH_EXPIRED
            429 -> AdapterErrorClass.RATE_LIMITED.also { quotaTracker.markExhausted(id) }
            in 500..599 -> AdapterErrorClass.NETWORK
            else -> AdapterErrorClass.MODEL_ERROR
        }
        quotaTracker.recordError(id)
        return InferenceResult.Error(errClass, "Claude HTTP $code: ${raw.take(400)}")
    }

    companion object {
        const val KEY_SESSION = "claude_session_json"
        private val MEDIA_JSON = "application/json; charset=utf-8".toMediaType()
    }
}
