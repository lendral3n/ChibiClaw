package com.chibiclaw.ai.llm.adapters

import com.chibiclaw.ai.llm.AdapterCapability
import com.chibiclaw.ai.llm.AdapterErrorClass
import com.chibiclaw.ai.llm.AdapterQuotaTracker
import com.chibiclaw.ai.llm.AgentPrompt
import com.chibiclaw.ai.llm.InferenceAdapter
import com.chibiclaw.ai.llm.InferenceChunk
import com.chibiclaw.ai.llm.InferenceResult
import com.chibiclaw.ai.llm.PromptBuilder
import com.chibiclaw.ai.llm.session.GPTWebSession
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
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GPTWebAdapter — reverse-engineered chatgpt.com web session.
 *
 * Endpoint: `https://chatgpt.com/backend-api/conversation` POST + SSE.
 * Header `Authorization: Bearer {accessToken}` (di-extract dari /api/auth/session
 * pas WebView login).
 *
 * Sama spirit dengan ClaudeWebAdapter: tolerate session absent, return AUTH_EXPIRED.
 */
@Singleton
class GPTWebAdapter @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val quotaTracker: AdapterQuotaTracker,
) : InferenceAdapter {

    override val id = "gpt_web"
    override val capability = AdapterCapability(
        displayName = "ChatGPT (Web Session)",
        contextWindow = 128_000,
        supportsToolCalling = true,
        supportsStreaming = false,  // Phase 9: aggregate SSE; per-token Flow TBD
        supportsVision = true,
        supportsConstrainedDecoding = false,
        isLocal = false,
        estimatedTpsDecode = 70f,
        estimatedTpsPrefill = 1200f,
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

    @Volatile private var lastCallMs: Long = 0L

    override suspend fun isAvailable(): Boolean {
        val session = loadSession() ?: return false
        if (isSessionExpired(session)) return false
        quotaTracker.ensureConfig(id, capability.displayName, dailyQuota = -1)
        return quotaTracker.hasQuota(id)
    }

    override suspend fun complete(prompt: AgentPrompt): InferenceResult = withContext(Dispatchers.IO) {
        // Phase 9: per-call rate limiter 30s.
        val gap = System.currentTimeMillis() - lastCallMs
        if (gap in 1..MIN_CALL_GAP_MS) {
            kotlinx.coroutines.delay(MIN_CALL_GAP_MS - gap)
        }
        lastCallMs = System.currentTimeMillis()

        val session = loadSession()
            ?: return@withContext InferenceResult.Error(
                AdapterErrorClass.AUTH_EXPIRED,
                "Belum login ChatGPT. Setup wizard → Cloud → GPT login.",
            )
        if (isSessionExpired(session)) {
            return@withContext InferenceResult.Error(
                AdapterErrorClass.AUTH_EXPIRED,
                "Session ChatGPT expired. Re-login.",
            )
        }

        val messageId = UUID.randomUUID().toString()
        val convId = session.conversationId ?: UUID.randomUUID().toString()
        val body = buildJsonObject {
            put("action", "next")
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("id", messageId)
                    put("author", buildJsonObject { put("role", "user") })
                    put("content", buildJsonObject {
                        put("content_type", "text")
                        put("parts", buildJsonArray {
                            add(PromptBuilder.toGemmaFormat(prompt))
                        })
                    })
                })
            })
            put("conversation_id", convId)
            put("parent_message_id", UUID.randomUUID().toString())
            put("model", "auto")
            put("timezone_offset_min", -java.util.TimeZone.getDefault().rawOffset / 60_000)
        }

        val req = Request.Builder()
            .url("https://chatgpt.com/backend-api/conversation")
            .post(body.toString().toRequestBody(MEDIA_JSON))
            .header("User-Agent", session.userAgent)
            .header("Cookie", session.cookies.joinToString("; "))
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Accept", "text/event-stream")
            .header("Origin", "https://chatgpt.com")
            .header("Referer", "https://chatgpt.com/")
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
                        "ChatGPT SSE kosong: ${raw.take(400)}",
                    )
                }
                quotaTracker.recordSuccess(id)
                val latency = System.currentTimeMillis() - start
                Timber.d("GPT web ok ${text.length}c / ${latency}ms")
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
            Timber.e(t, "GPT exception")
            InferenceResult.Error(AdapterErrorClass.UNKNOWN, t.message ?: t.javaClass.simpleName)
        }
    }

    override fun stream(prompt: AgentPrompt): Flow<InferenceChunk> {
        return flowOf(InferenceChunk(text = "", isLast = true))
    }

    override suspend fun shutdown() = Unit

    private fun loadSession(): GPTWebSession? {
        val raw = securePreferences.getString(KEY_SESSION) ?: return null
        return runCatching { json.decodeFromString<GPTWebSession>(raw) }.getOrNull()
    }

    private fun isSessionExpired(session: GPTWebSession): Boolean {
        val ageDays = (System.currentTimeMillis() - session.createdAtMs) / (1000L * 60 * 60 * 24)
        return ageDays >= session.maxAgeDays
    }

    /**
     * Parse SSE. ChatGPT emit incremental `message.author.role=assistant`
     * dengan `content.parts[0]` accumulating string. Ambil final part dari
     * frame terakhir yang valid.
     */
    private fun parseSse(raw: String): String {
        var lastText = ""
        raw.split("\n\n").forEach { frame ->
            val dataLine = frame.lineSequence().firstOrNull { it.startsWith("data:") } ?: return@forEach
            val payload = dataLine.removePrefix("data:").trim()
            if (payload == "[DONE]" || payload.isEmpty()) return@forEach
            runCatching {
                val s = payload
                val partsAnchor = "\"parts\":[\""
                val idx = s.indexOf(partsAnchor)
                if (idx >= 0) {
                    val tail = s.substring(idx + partsAnchor.length)
                    val end = tail.indexOf("\"]")
                    if (end >= 0) {
                        val piece = tail.substring(0, end)
                            .replace("\\n", "\n")
                            .replace("\\\"", "\"")
                        if (piece.length > lastText.length) lastText = piece
                    }
                }
            }
        }
        return lastText
    }

    private suspend fun mapHttp(code: Int, raw: String): InferenceResult.Error {
        val errClass = when (code) {
            401, 403 -> AdapterErrorClass.AUTH_EXPIRED
            429 -> AdapterErrorClass.RATE_LIMITED.also { quotaTracker.markExhausted(id) }
            in 500..599 -> AdapterErrorClass.NETWORK
            else -> AdapterErrorClass.MODEL_ERROR
        }
        quotaTracker.recordError(id)
        return InferenceResult.Error(errClass, "GPT HTTP $code: ${raw.take(400)}")
    }

    companion object {
        const val KEY_SESSION = "gpt_session_json"
        private val MEDIA_JSON = "application/json; charset=utf-8".toMediaType()
        private const val MIN_CALL_GAP_MS = 30_000L
    }
}
