package com.chibiclaw.voice.stt

import android.content.Context
import com.chibiclaw.voice.audio.MicCapture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WhisperStt — speech-to-text via sherpa-onnx Whisper small Q5_1.
 *
 * Status Phase 2 (audit-ready):
 *  - Reflection-based init (tahan kalau sherpa-onnx dep belum di-enable atau
 *    API berubah). Model file expected di `assets/models/sherpa_whisper_small/`
 *    atau `filesDir/models/sherpa_whisper_small/`.
 *  - Fallback "echo" mode kalau init gagal — return text yang menandakan STT
 *    belum live, supaya end-to-end agent loop tetap testable di dev.
 *
 * Sub-milestone Phase 2:
 *  1. Push sherpa-onnx Whisper small files (encoder.onnx + decoder.onnx +
 *     tokens.txt) ke `/data/data/com.chibiclaw/files/models/sherpa_whisper_small/`
 *  2. Enable `com.k2-fsa.sherpa-onnx:sherpa-onnx-android` dep di build.gradle
 *  3. Replace [transcribeOnnx] implementation real
 */
@Singleton
class WhisperStt @Inject constructor(
    @ApplicationContext private val context: Context,
    private val micCapture: MicCapture,
) {
    private val mutex = Mutex()
    @Volatile private var recognizer: Any? = null
    @Volatile private var initFailed: Boolean = false

    suspend fun init() = mutex.withLock {
        if (recognizer != null || initFailed) return@withLock
        initFailed = !tryInitSherpa()
        if (initFailed) {
            Timber.w("WhisperStt: sherpa-onnx init gagal, mode fallback echo aktif")
        }
    }

    /**
     * Streaming transcribe. Phase 2: collect mic stream, accumulate ke buffer,
     * call recognizer at end of utterance (simplified — Phase 2 polish bisa
     * tambah VAD endpoint detection).
     */
    fun streamingTranscribe(audioFlow: Flow<ShortArray>): Flow<SttResult> = flow {
        if (recognizer == null && !initFailed) init()

        // Accumulate audio sampai stream ends (Phase 2 simple — call site stop
        // mic saat user tap stop atau VAD silence).
        val allSamples = audioFlow.fold(ShortArray(0)) { acc, chunk -> acc + chunk }
        Timber.d("Whisper accumulated ${allSamples.size} samples (${allSamples.size / 16000f}s)")

        val finalText = if (recognizer != null) {
            runCatching { transcribeOnnx(allSamples) }.getOrElse { e ->
                Timber.w(e, "Whisper inference failed, fallback")
                fallbackEcho(allSamples)
            }
        } else {
            fallbackEcho(allSamples)
        }
        emit(SttResult.Final(finalText))
    }

    /**
     * Reflection-based sherpa-onnx init.
     *
     * Expected class: `com.k2fsa.sherpa.onnx.OfflineRecognizer` (atau Online).
     * Saat sherpa-onnx dep dan model file siap, refactor ke direct API.
     */
    private fun tryInitSherpa(): Boolean {
        val modelDir = File(context.filesDir, "models/sherpa_whisper_small")
        if (!modelDir.exists() || !modelDir.isDirectory) {
            Timber.w("Whisper model dir tidak ada: ${modelDir.absolutePath}")
            return false
        }
        return try {
            // Coba locate sherpa-onnx class — kalau dep tidak di-enable, throw
            Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer")
            // Phase 2 sub-milestone: bind real recognizer di sini
            Timber.i("Sherpa-onnx detected (Phase 2 sub-milestone: bind real API)")
            // Sementara return false supaya fallback aktif sampai full bind
            false
        } catch (t: Throwable) {
            Timber.w("Sherpa-onnx dep tidak tersedia: ${t.message}")
            false
        }
    }

    private fun transcribeOnnx(samples: ShortArray): String {
        throw NotImplementedError(
            "Sherpa-onnx Whisper transcribe binding belum di-implement. " +
                "Phase 2 sub-milestone: setelah dep + model file siap, panggil " +
                "recognizer.acceptWaveform → recognizer.decode → recognizer.getText"
        )
    }

    private fun fallbackEcho(samples: ShortArray): String {
        // Dev mode: kasih sinyal ke user bahwa STT belum live tapi mic capture
        // bekerja, supaya end-to-end agent loop testable.
        val durationSec = "%.1f".format(samples.size / MicCapture.SAMPLE_RATE.toFloat())
        return "[STT belum aktif — ${durationSec}s audio diterima. Push sherpa-onnx model + enable dep untuk activate.]"
    }
}

sealed class SttResult {
    data class Partial(val text: String) : SttResult()
    data class Final(val text: String) : SttResult()
}
