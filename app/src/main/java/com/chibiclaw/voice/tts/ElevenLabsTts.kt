package com.chibiclaw.voice.tts

import com.chibiclaw.data.prefs.SecurePreferences
import com.chibiclaw.voice.audio.AudioTrackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
 * ElevenLabsTts — streaming PCM TTS via ElevenLabs API v3.
 *
 * Voice ID Fuu: gMIZZcmZCnyySbZdSZrZ (Lendra subscription).
 * API key disimpan di SecurePreferences (key: KEY_ELEVENLABS_API_KEY).
 *
 * Phase 2 implementation:
 *  - POST /v1/text-to-speech/{voice_id}/stream dengan output_format=pcm_22050
 *  - Stream chunks ke AudioTrack langsung (~22050 Hz mono PCM 16-bit)
 *  - VoiceSettings dinamis per emotion tag (stability + style)
 */
@Singleton
class ElevenLabsTts @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val audioTrackManager: AudioTrackManager,
    private val auditLogger: com.chibiclaw.compliance.AuditLogger,
) {

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun hasApiKey(): Boolean = !securePreferences.getString(KEY_ELEVENLABS_API_KEY).isNullOrBlank()

    suspend fun speak(text: String, emotion: String? = null): TtsResult = withContext(Dispatchers.IO) {
        val apiKey = securePreferences.getString(KEY_ELEVENLABS_API_KEY)
            ?: return@withContext TtsResult.Error("ElevenLabs API key belum di-set di Settings")
        auditLogger.log(
            actionType = com.chibiclaw.data.database.AuditActionType.TTS_PLAYBACK,
            dataSummary = "TTS speak ${text.length} chars emotion=$emotion",
        )

        val processedText = applyEmotionTag(text, emotion)
        val settings = computeVoiceSettings(emotion)
        val payload = TtsPayload(
            text = processedText,
            modelId = "eleven_v3",
            voiceSettings = settings,
            outputFormat = "pcm_${SAMPLE_RATE}",
        )

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$VOICE_ID_FUU/stream?output_format=pcm_${SAMPLE_RATE}")
            .post(json.encodeToString(TtsPayload.serializer(), payload).toRequestBody(JSON_MEDIA))
            .header("xi-api-key", apiKey)
            .header("Accept", "audio/pcm")
            .header("Content-Type", "application/json")
            .build()

        return@withContext runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string()
                    return@runCatching TtsResult.Error(
                        "ElevenLabs HTTP ${response.code}: ${body?.take(200) ?: "no body"}"
                    )
                }
                val stream = response.body?.byteStream()
                    ?: return@runCatching TtsResult.Error("Empty response body")

                audioTrackManager.open(sampleRate = SAMPLE_RATE)
                val buffer = ByteArray(BUFFER_SIZE)
                var totalBytes = 0
                try {
                    while (true) {
                        val read = stream.read(buffer)
                        if (read == -1) break
                        audioTrackManager.write(buffer, 0, read)
                        totalBytes += read
                    }
                } finally {
                    audioTrackManager.close()
                }
                Timber.d("ElevenLabs streamed ${totalBytes / 1024} KB PCM")
                TtsResult.Success(bytesStreamed = totalBytes)
            }
        }.getOrElse { t ->
            Timber.e(t, "ElevenLabs TTS exception")
            TtsResult.Error(t.message ?: t.javaClass.simpleName)
        }
    }

    /**
     * Apply emotion tag inline (ElevenLabs v3 format).
     */
    private fun applyEmotionTag(text: String, emotion: String?): String {
        if (emotion.isNullOrBlank()) return text
        return when (emotion.lowercase()) {
            "joy", "happy", "excited" -> "[excited] $text"
            "sad", "empathetic" -> "[whispers] $text"
            "angry" -> "[sternly] $text"
            "surprised" -> "[gasps] $text"
            "satisfied" -> "[cheerful] $text"
            "uncertain", "anxious" -> "[softly] $text"
            else -> text
        }
    }

    /**
     * Dynamic voice settings per emotion (stability, similarity, style).
     */
    private fun computeVoiceSettings(emotion: String?): VoiceSettings {
        return when (emotion?.lowercase()) {
            "joy", "happy", "excited" -> VoiceSettings(stability = 0.4f, similarityBoost = 0.75f, style = 0.6f)
            "sad", "empathetic" -> VoiceSettings(stability = 0.7f, similarityBoost = 0.75f, style = 0.2f)
            "angry" -> VoiceSettings(stability = 0.5f, similarityBoost = 0.75f, style = 0.7f)
            "satisfied" -> VoiceSettings(stability = 0.55f, similarityBoost = 0.75f, style = 0.5f)
            "uncertain", "anxious" -> VoiceSettings(stability = 0.65f, similarityBoost = 0.75f, style = 0.3f)
            else -> VoiceSettings(stability = 0.55f, similarityBoost = 0.75f, style = 0.3f)
        }
    }

    companion object {
        const val VOICE_ID_FUU = "gMIZZcmZCnyySbZdSZrZ"
        const val SAMPLE_RATE = 22050
        const val BUFFER_SIZE = 4096
        const val KEY_ELEVENLABS_API_KEY = "elevenlabs_api_key"
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}

@Serializable
data class TtsPayload(
    val text: String,
    @SerialName("model_id") val modelId: String,
    @SerialName("voice_settings") val voiceSettings: VoiceSettings,
    @SerialName("output_format") val outputFormat: String,
)

@Serializable
data class VoiceSettings(
    val stability: Float,
    @SerialName("similarity_boost") val similarityBoost: Float,
    val style: Float = 0.3f,
    @SerialName("use_speaker_boost") val useSpeakerBoost: Boolean = true,
)

sealed class TtsResult {
    data class Success(val bytesStreamed: Int) : TtsResult()
    data class Error(val message: String) : TtsResult()
}
