package com.chibiclaw.vision.llm

/**
 * Prompt template untuk MiniCPM-V grounding / describe.
 *
 * MiniCPM-V 4.6 expect chat format `<|im_start|>user\n...<|im_end|>\n<|im_start|>assistant\n`.
 * Image token diinjeksi via JNI side dengan placeholder `(<image>./</image>)`.
 *
 * Default output kita constrain ke JSON supaya parser deterministik.
 */
object VisionPromptBuilder {

    fun grounding(query: String, screenWidth: Int, screenHeight: Int): String = buildString {
        append("(<image>./</image>)")
        append("\n")
        append("Layar Android ${screenWidth}x${screenHeight} px. ")
        append("Cari elemen UI yang match query, return JSON koordinat tap:\n")
        append("{\"x\":<int px>,\"y\":<int px>,\"confidence\":<0-1 float>,\"label\":\"<short desc>\"}\n")
        append("Hanya satu elemen paling match. Kalau ambigu / tidak ketemu, return ")
        append("{\"confidence\":0,\"label\":\"not_found\"}.\n")
        append("Query: $query")
    }

    fun describe(query: String): String = buildString {
        append("(<image>./</image>)")
        append("\n")
        append("Describe relevan untuk query berikut. Padat (max 2 kalimat).\n")
        append("Query: $query")
    }

    fun extractText(): String = buildString {
        append("(<image>./</image>)")
        append("\n")
        append("Extract semua text visible di image, urut top-bottom left-right. ")
        append("Jangan invent text yang tidak ada. Plain text, no markdown.")
    }
}

/**
 * Output grounding parser hasil — match JSON dari MiniCPM-V atau Gemini.
 */
data class GroundingResult(
    val x: Int,
    val y: Int,
    val confidence: Float,
    val label: String,
) {
    val found: Boolean get() = confidence > 0f && label != "not_found"
}
