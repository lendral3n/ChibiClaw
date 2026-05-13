package com.chibiclaw.voice.emotion

import javax.inject.Inject
import javax.inject.Singleton

/**
 * EmotionDetector — facade combining Wav2Small audio emotion + roberta text
 * emotion. Return EmotionContext yang dikirim ke LLM sebagai signal.
 *
 * Tidak ada policy code — LLM yang interpret + putus emotion response Fuu.
 */
@Singleton
class EmotionDetector @Inject constructor(
    private val audioEmotion: Wav2SmallEmotion,
    private val textEmotion: TextEmotionClassifier,
) {

    @Volatile private var lastAudioVad: VadVector? = null
    @Volatile private var lastTextEmotions: Map<String, Float> = emptyMap()

    /**
     * Observasi audio user (real PCM dari mic). Update internal state untuk
     * dikirim ke LLM di context build berikutnya.
     */
    suspend fun observeAudio(pcm: ShortArray) {
        lastAudioVad = audioEmotion.analyze(pcm)
    }

    /**
     * Observasi text user (post-STT atau text input).
     */
    suspend fun observeText(text: String) {
        lastTextEmotions = textEmotion.classify(text)
    }

    /**
     * Current context snapshot — dikirim ke ContextBuilder.
     */
    fun current(): EmotionContext = EmotionContext(
        audioVad = lastAudioVad,
        textEmotions = lastTextEmotions,
    )

    /**
     * Reset state (di-call setelah task complete supaya emotion tidak bocor
     * antar task).
     */
    fun reset() {
        lastAudioVad = null
        lastTextEmotions = emptyMap()
    }
}
