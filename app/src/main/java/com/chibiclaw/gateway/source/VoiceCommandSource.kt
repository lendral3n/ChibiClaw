package com.chibiclaw.gateway.source

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.chibiclaw.ai.GemmaEngineManager
import com.chibiclaw.ai.GemmaInference
import com.chibiclaw.debug.DevLogger
import com.chibiclaw.gateway.CommandGateway
import com.chibiclaw.gateway.CommandSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

enum class VoiceState { IDLE, LISTENING, PROCESSING, ERROR }

/**
 * P3.4 — VoiceCommandSource (Gemma native audio)
 *
 * Captures 16 kHz mono PCM via [AudioRecord] and transcribes it offline
 * using Gemma 3n E4B's native audio tower (see [GemmaInference.transcribeAudio]).
 * The recognised text is then pushed to [CommandGateway] just like any
 * other command, so the rest of the orchestrator / safety / state machine
 * pipeline is untouched.
 *
 * Why not Android SpeechRecognizer?
 *   - Requires a Google Play Services backend (online on most devices)
 *   - Separate codepath, defeats the "everything runs on-device Gemma"
 *     promise
 *   - Gemma 3n's audio tower handles Indonesian + code-switching better
 *     than SpeechRecognizer's id-ID model
 *
 * Recording stops automatically after ~1.2 s of trailing silence (simple
 * RMS-based VAD), or when [stopListening] is called, or when the buffer
 * exceeds the 30-second cap.
 */
@Singleton
class VoiceCommandSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val commandGateway: CommandGateway,
    private val gemmaInference: GemmaInference,
    private val engineManager: GemmaEngineManager,
    private val devLogger: DevLogger
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var captureJob: Job? = null

    @Volatile
    private var stopRequested: Boolean = false

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _lastTranscript = MutableStateFlow("")
    val lastTranscript: StateFlow<String> = _lastTranscript.asStateFlow()

    fun startListening() {
        if (_voiceState.value == VoiceState.LISTENING ||
            _voiceState.value == VoiceState.PROCESSING
        ) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission not granted")
            devLogger.e("VOICE", "RECORD_AUDIO permission missing — user must grant it")
            _voiceState.value = VoiceState.ERROR
            return
        }

        stopRequested = false
        _voiceState.value = VoiceState.LISTENING
        devLogger.i("VOICE", "Recording started (16 kHz mono PCM → Gemma audio)")

        captureJob = scope.launch {
            val pcm = withContext(Dispatchers.IO) { captureUntilSilence() }
            if (pcm == null || pcm.isEmpty()) {
                devLogger.w("VOICE", "Capture produced no audio")
                _voiceState.value = VoiceState.IDLE
                return@launch
            }
            devLogger.i("VOICE", "Captured ${pcm.size / 1024} KB PCM — sending to Gemma")
            _voiceState.value = VoiceState.PROCESSING

            // Wrap into WAV so Gemma's audio loader can parse header info.
            val wav = pcmToWav(pcm, SAMPLE_RATE)

            val text = withContext(engineManager.engineDispatcher) {
                gemmaInference.transcribeAudio(wav, language = "Indonesian")
            }

            if (text.isBlank()) {
                devLogger.w("VOICE", "Transcription returned empty")
                _voiceState.value = VoiceState.ERROR
                return@launch
            }

            _lastTranscript.value = text
            devLogger.i("VOICE", "Transcribed: \"$text\" — submitting to gateway")
            commandGateway.submitDirect(text, CommandSource.VOICE)
            _voiceState.value = VoiceState.IDLE
        }
    }

    fun stopListening() {
        stopRequested = true
    }

    fun destroy() {
        stopRequested = true
        captureJob?.cancel()
        captureJob = null
    }

    /**
     * Reads PCM frames from [AudioRecord] into a byte buffer until one of:
     *   1. [stopRequested] is set (user tapped mic to stop)
     *   2. >= [SILENCE_MS] ms of trailing RMS below [SILENCE_RMS] after at
     *      least [MIN_VOICE_MS] ms of voiced audio
     *   3. Total buffer reaches [MAX_CAPTURE_BYTES]
     */
    private fun captureUntilSilence(): ByteArray? {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "AudioRecord construction denied: ${e.message}")
            devLogger.e("VOICE", "AudioRecord denied: ${e.message}")
            return null
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            record.release()
            return null
        }

        val out = ByteArrayOutputStream()
        val frame = ByteArray(minBuf)
        var silenceMs = 0
        var voicedMs = 0
        try {
            record.startRecording()
            while (!stopRequested) {
                val read = record.read(frame, 0, frame.size)
                if (read <= 0) continue
                out.write(frame, 0, read)
                val rms = rmsShorts(frame, read)
                val frameMs = (read * 1000L / (SAMPLE_RATE * 2)).toInt() // 16-bit mono
                if (rms < SILENCE_RMS) {
                    silenceMs += frameMs
                } else {
                    silenceMs = 0
                    voicedMs += frameMs
                }
                if (voicedMs >= MIN_VOICE_MS && silenceMs >= SILENCE_MS) break
                if (out.size() >= MAX_CAPTURE_BYTES) break
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recording error: ${e.message}")
            devLogger.e("VOICE", "Recording failed: ${e::class.simpleName}: ${e.message}")
        } finally {
            try { record.stop() } catch (_: Exception) {}
            record.release()
        }
        return out.toByteArray()
    }

    /** RMS of interleaved 16-bit LE PCM in [buf] over the first [len] bytes. */
    private fun rmsShorts(buf: ByteArray, len: Int): Double {
        if (len < 2) return 0.0
        var sum = 0.0
        var i = 0
        val count = len and 1.inv()
        while (i < count) {
            val lo = buf[i].toInt() and 0xFF
            val hi = buf[i + 1].toInt()
            val s = (hi shl 8) or lo
            sum += (s * s).toDouble()
            i += 2
        }
        return sqrt(sum / (count / 2))
    }

    /** Wrap raw 16-bit mono PCM in a minimal WAV header. */
    private fun pcmToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val byteRate = sampleRate * 2
        val totalDataLen = pcm.size + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)                 // PCM chunk size
        header.putShort(1)                // PCM format
        header.putShort(1)                // mono
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(2)                // block align (mono 16-bit)
        header.putShort(16)               // bits per sample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcm.size)
        return header.array() + pcm
    }

    companion object {
        private const val TAG = "VoiceCommandSource"
        private const val SAMPLE_RATE = 16_000
        private const val SILENCE_RMS = 600.0          // below this is "silence"
        private const val SILENCE_MS = 1_200            // trailing silence to stop
        private const val MIN_VOICE_MS = 400            // ignore early silence
        private const val MAX_CAPTURE_BYTES = 30 * SAMPLE_RATE * 2 // 30 s cap
    }
}
