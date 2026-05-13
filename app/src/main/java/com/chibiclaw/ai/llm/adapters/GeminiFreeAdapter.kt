package com.chibiclaw.ai.llm.adapters

import com.chibiclaw.ai.llm.AdapterCapability
import com.chibiclaw.ai.llm.AdapterErrorClass
import com.chibiclaw.ai.llm.AdapterQuotaTracker
import com.chibiclaw.ai.llm.AgentPrompt
import com.chibiclaw.ai.llm.InferenceAdapter
import com.chibiclaw.ai.llm.InferenceChunk
import com.chibiclaw.ai.llm.InferenceResult
import com.chibiclaw.ai.llm.PromptBuilder
import com.chibiclaw.data.prefs.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 * GeminiFreeAdapter — Google AI Studio free tier (1500 req/day).
 *
 * Endpoint: POST `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}`
 *
 * Field key di SecurePreferences: `gemini_api_key`.
 *
 * Constrained-decoding via `generationConfig.responseMimeType = "application/json"`.
 * Tidak pakai response_schema strict karena tool catalog dinamis — kita biarkan
 * Gemini emit JSON struct sendiri sesuai instruksi system prompt.
 *
 * Quota: tracked via [AdapterQuotaTracker]. Daily limit 1500.
 */
@Singleton
class GeminiFreeAdapter @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val quotaTracker: AdapterQuotaTracker,
) : InferenceAdapter {

    override val id = "gemini_free"
    override val capability = AdapterCapability(
        displayName = "Gemini 2.5 Flash (Free)",
        contextWindow = 1_000_000,
        supportsToolCalling = true,
        supportsStreaming = true,
        supportsVision = true,
        supportsConstrainedDecoding = true,
        isLocal = false,
        estimatedTpsDecode = 250f,
        estimatedTpsPrefill = 3000f,
        requiresAuth = true,
    )

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun isAvailable(): Boolean {
        val key = securePreferences.getString(KEY_API)
        if (key.isNullOrBlank()) return false
        // Ensure config row + check quota.
        quotaTracker.ensureConfig(id, capability.displayName, DAILY_QUOTA_FREE)
        return quotaTracker.hasQuota(id)
    }

    override suspend fun complete(prompt: AgentPrompt): InferenceResult = withContext(Dispatchers.IO) {
        val apiKey = securePreferences.getString(KEY_API)
            ?: return@withContext InferenceResult.Error(
                AdapterErrorClass.AUTH_EXPIRED,
                "Gemini API key belum di-set. Setup wizard → Gemini step.",
            )

        if (!quotaTracker.hasQuota(id)) {
            return@withContext InferenceResult.Error(
                AdapterErrorClass.RATE_LIMITED,
                "Gemini free quota habis hari ini (limit $DAILY_QUOTA_FREE/day).",
            )
        }

        val body = buildRequestBody(prompt)
        val url = "$BASE_URL/$MODEL_ID:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(MEDIA_JSON))
            .build()

        val start = System.currentTimeMillis()
        try {
            http.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use mapHttpError(resp.code, raw)
                }
                val text = parseCandidateText(raw)
                if (text.isNullOrBlank()) {
                    quotaTracker.recordError(id)
                    return@use InferenceResult.Error(
                        AdapterErrorClass.MODEL_ERROR,
                        "Gemini response kosong / malformed: ${raw.take(400)}",
                    )
                }
                quotaTracker.recordSuccess(id)
                val latency = System.currentTimeMillis() - start
                Timber.d("Gemini call ok ${text.length} chars / ${latency}ms")
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
            Timber.e(t, "Gemini exception")
            InferenceResult.Error(AdapterErrorClass.UNKNOWN, t.message ?: t.javaClass.simpleName)
        }
    }

    override fun stream(prompt: AgentPrompt): Flow<InferenceChunk> {
        // Phase 4 sub-milestone: implement via streamGenerateContent + SSE parse.
        // Sekarang return single chunk dari complete() — caller fallback aman.
        return flowOf(InferenceChunk(text = "", isLast = true))
    }

    override suspend fun shutdown() = Unit

    private fun buildRequestBody(prompt: AgentPrompt): JsonObject {
        val gemmaFormatted = PromptBuilder.toGemmaFormat(prompt)
        // Pisah system instruction supaya Gemini bisa anchor persona/tools.
        val systemPart = gemmaFormatted.substringAfter("<start_of_turn>system\n", "")
            .substringBefore("<end_of_turn>")
            .ifBlank { gemmaFormatted }
        val userPart = gemmaFormatted.substringAfter("<start_of_turn>user\n", "")
            .substringBefore("<end_of_turn>")
            .ifBlank { prompt.taskGoal }

        return buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", systemPart) })
                })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", userPart) })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseMimeType", "application/json")
                put("temperature", 0.7)
                put("topK", 40)
                put("topP", 0.95)
                put("maxOutputTokens", 4096)
            })
            put("safetySettings", buildJsonArray {
                listOf(
                    "HARM_CATEGORY_HARASSMENT",
                    "HARM_CATEGORY_HATE_SPEECH",
                    "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                    "HARM_CATEGORY_DANGEROUS_CONTENT",
                ).forEach { cat ->
                    add(buildJsonObject {
                        put("category", cat)
                        put("threshold", "BLOCK_ONLY_HIGH")
                    })
                }
            })
        }
    }

    private fun parseCandidateText(raw: String): String? = runCatching {
        val obj = json.parseToJsonElement(raw).jsonObject
        val candidates = obj["candidates"]?.jsonArray ?: return@runCatching null
        val first = candidates.firstOrNull()?.jsonObject ?: return@runCatching null
        val content = first["content"]?.jsonObject ?: return@runCatching null
        val parts = content["parts"]?.jsonArray ?: return@runCatching null
        parts.joinToString("") { part ->
            part.jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
        }.ifBlank { null }
    }.getOrNull()

    private suspend fun mapHttpError(code: Int, raw: String): InferenceResult.Error {
        val errClass = when (code) {
            401, 403 -> AdapterErrorClass.AUTH_EXPIRED
            429 -> AdapterErrorClass.RATE_LIMITED.also { quotaTracker.markExhausted(id) }
            in 500..599 -> AdapterErrorClass.NETWORK
            else -> AdapterErrorClass.MODEL_ERROR
        }
        quotaTracker.recordError(id)
        return InferenceResult.Error(errClass, "Gemini HTTP $code: ${raw.take(400)}")
    }

    companion object {
        const val KEY_API = "gemini_api_key"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODEL_ID = "gemini-2.5-flash"
        private const val DAILY_QUOTA_FREE = 1500
        private val MEDIA_JSON = "application/json; charset=utf-8".toMediaType()
    }
}
