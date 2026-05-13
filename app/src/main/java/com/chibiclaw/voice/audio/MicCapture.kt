package com.chibiclaw.voice.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MicCapture — AudioRecord wrapper untuk PCM 16kHz mono.
 *
 * Phase 2: streaming Flow<ShortArray> ke STT downstream.
 * Phase 8: support multi-consumer via SharedFlow (mic emit ke STT + emotion paralel).
 */
@Singleton
class MicCapture @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start streaming. Caller harus collect Flow di scope yang bisa di-cancel
     * (mis. viewModelScope) supaya AudioRecord lifecycle bersih.
     *
     * Buffer size: ~50ms chunk (800 samples @ 16kHz).
     */
    @SuppressLint("MissingPermission")
    fun stream(): Flow<ShortArray> = flow {
        if (!hasPermission()) {
            throw SecurityException("RECORD_AUDIO permission not granted")
        }

        val sampleRate = SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = (minBuffer * 2).coerceAtLeast(CHUNK_SAMPLES * 2 * Short.SIZE_BYTES)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("AudioRecord init failed")
        }

        recorder.startRecording()
        Timber.d("MicCapture started (sampleRate=$sampleRate, bufferSize=$bufferSize)")
        val chunk = ShortArray(CHUNK_SAMPLES)
        try {
            while (true) {
                val read = recorder.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) break
                emit(chunk.copyOf(read))
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            Timber.d("MicCapture stopped")
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHUNK_SAMPLES = 800   // ~50ms
    }
}
