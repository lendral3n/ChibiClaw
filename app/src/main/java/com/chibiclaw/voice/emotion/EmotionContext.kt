package com.chibiclaw.voice.emotion

/**
 * Emotion signal yang dikirim ke LLM sebagai context input.
 *
 * BUKAN policy code yang menentukan emotion response Fuu — LLM yang interpret +
 * decide emotion output. Code cuma sediakan signal mentah.
 */
data class EmotionContext(
    val audioVad: VadVector? = null,
    val textEmotions: Map<String, Float> = emptyMap(),
) {
    fun isEmpty(): Boolean = audioVad == null && textEmotions.isEmpty()

    fun toPromptText(): String = buildString {
        if (audioVad != null) {
            append("audio_emotion: V=${"%.2f".format(audioVad.valence)} ")
            append("A=${"%.2f".format(audioVad.arousal)} ")
            append("D=${"%.2f".format(audioVad.dominance)}")
        }
        if (textEmotions.isNotEmpty()) {
            if (audioVad != null) append(" | ")
            val top = textEmotions.entries.sortedByDescending { it.value }.take(3)
            append("text_emotion: ")
            top.joinTo(this, ", ") { (label, score) -> "$label=${"%.2f".format(score)}" }
        }
    }
}

/**
 * Valence-Arousal-Dominance vector (continuous emotion representation).
 * - valence: pleasant (1) ↔ unpleasant (-1)
 * - arousal: active (1) ↔ passive (-1)
 * - dominance: in-control (1) ↔ controlled (-1)
 */
data class VadVector(
    val valence: Float,
    val arousal: Float,
    val dominance: Float,
)
