package com.chibiclaw.voice

import com.chibiclaw.agent.ConversationManager
import com.chibiclaw.voice.audio.MicCapture
import com.chibiclaw.voice.audio.MicLock
import com.chibiclaw.voice.emotion.EmotionDetector
import com.chibiclaw.voice.stt.SttResult
import com.chibiclaw.voice.stt.WhisperStt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
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
) {

    @Volatile private var activeJob: Job? = null

    fun isRecording(): Boolean = activeJob?.isActive == true

    /**
     * Start record. Caller (mic button) panggil stop() saat tap-stop atau
     * VAD silence detect (Phase 2 polish).
     */
    fun start(scope: CoroutineScope, onTranscribed: suspend (String) -> Unit) {
        if (isRecording()) return

        activeJob = scope.launch {
            try {
                micLock.withMic {
                    val audioFlow = micCapture.stream()
                        .takeWhile { activeJob?.isActive == true }
                        .onEach { chunk ->
                            // Observe untuk emotion detect di akhir
                            // (Phase 2 simple: full buffer; Phase 6 polish: chunked emotion)
                        }
                    whisperStt.streamingTranscribe(audioFlow).collect { result ->
                        when (result) {
                            is SttResult.Final -> {
                                Timber.i("STT final: ${result.text.take(80)}")
                                emotionDetector.observeText(result.text)
                                onTranscribed(result.text)
                                if (result.text.isNotBlank() && !result.text.startsWith("[STT belum aktif")) {
                                    conversationManager.handleUserInput(result.text)
                                }
                            }
                            is SttResult.Partial -> {
                                // Phase 2 polish: stream partial ke UI buat live caption
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t, "VoicePipeline error")
            }
        }
    }

    fun stop() {
        activeJob?.cancel()
        activeJob = null
    }
}
