package com.chibiclaw.voice

import com.chibiclaw.agent.ConversationManager
import com.chibiclaw.compliance.AuditLogger
import com.chibiclaw.data.database.AuditActionType
import com.chibiclaw.data.database.AuditResultStatus
import com.chibiclaw.voice.audio.MicCapture
import com.chibiclaw.voice.audio.MicLock
import com.chibiclaw.voice.emotion.EmotionDetector
import com.chibiclaw.voice.stt.SttResult
import com.chibiclaw.voice.stt.WhisperStt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VoicePipelineOrchestrator — coordinate mic capture → STT → emotion observe
 * → ConversationManager enqueue task.
 *
 * Called dari OverlayChatPanel saat user tap mic button.
 */
@Singleton
class VoicePipelineOrchestrator @Inject constructor(
    private val micLock: MicLock,
    private val micCapture: MicCapture,
    private val whisperStt: WhisperStt,
    private val emotionDetector: EmotionDetector,
    private val conversationManager: ConversationManager,
    private val auditLogger: AuditLogger,
) {

    @Volatile private var activeJob: Job? = null

    fun isRecording(): Boolean = activeJob?.isActive == true

    /**
     * Start record. Caller (mic button) panggil stop() saat tap-stop atau
     * VAD silence detect (Phase 2 polish).
     */
    fun start(scope: CoroutineScope, onTranscribed: suspend (String) -> Unit) {
        if (isRecording()) return

        // Phase 9: audit log MIC_ACTIVATED (Phase 2 had infrastructure tapi tidak di-call).
        auditLogger.log(
            actionType = AuditActionType.MIC_ACTIVATED,
            dataSummary = "Mic capture started by user tap",
        )

        activeJob = scope.launch {
            try {
                micLock.withMic {
                    val audioFlow = micCapture.stream()
                        .takeWhile { activeJob?.isActive == true }
                        .onEach { _ ->
                            // Phase 6 polish: chunked emotion detect per audio chunk.
                        }
                    whisperStt.streamingTranscribe(audioFlow).collect { result ->
                        when (result) {
                            is SttResult.Final -> {
                                Timber.i("STT final: ${result.text.take(80)}")
                                auditLogger.log(
                                    actionType = AuditActionType.STT_RESULT,
                                    dataSummary = "STT final ${result.text.length} chars",
                                )
                                emotionDetector.observeText(result.text)
                                onTranscribed(result.text)
                                if (result.text.isNotBlank() && !result.text.startsWith("[STT belum aktif")) {
                                    conversationManager.handleUserInput(result.text)
                                }
                            }
                            is SttResult.Partial -> {
                                // Phase 2 polish: stream partial ke UI buat live caption.
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t, "VoicePipeline error")
                auditLogger.log(
                    actionType = AuditActionType.STT_RESULT,
                    dataSummary = "Voice pipeline exception: ${t.message?.take(80)}",
                    resultStatus = AuditResultStatus.FAILED,
                )
            }
        }
    }

    fun stop() {
        activeJob?.cancel()
        activeJob = null
        auditLogger.log(
            actionType = AuditActionType.MIC_DEACTIVATED,
            dataSummary = "Mic capture stopped",
        )
    }
}
