package com.chibiclaw.voice

import com.chibiclaw.data.database.TaskEntity
import com.chibiclaw.data.database.TaskStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ResponseComposer — translate task result + emotion tag → text + emotion
 * untuk TTS playback.
 *
 * Phase 2: simple — pakai resultSummary/errorMessage langsung sebagai text,
 * emotion tag pass-through.
 * Phase 6 polish: pre-recorded sample injection (giggle, sigh, breath) untuk
 * intensity tinggi.
 */
@Singleton
class ResponseComposer @Inject constructor() {

    fun compose(task: TaskEntity): ComposedResponse {
        val text = when (task.status) {
            TaskStatus.COMPLETED -> task.resultSummary
                ?: "Selesai"
            TaskStatus.FAILED -> "Maaf, ${task.errorMessage ?: "ada masalah"}"
            TaskStatus.AWAITING_USER -> task.errorMessage?.removePrefix("awaiting_user: ")
                ?: "Aku butuh info tambahan."
            else -> null
        } ?: return ComposedResponse.silent()

        return ComposedResponse(
            text = text,
            emotionTag = task.emotionTag,
            preRecordedPrefix = null,  // Phase 6 polish
        )
    }
}

data class ComposedResponse(
    val text: String,
    val emotionTag: String? = null,
    val preRecordedPrefix: String? = null,
) {
    val isSilent: Boolean get() = text.isBlank()

    companion object {
        fun silent() = ComposedResponse(text = "")
    }
}
