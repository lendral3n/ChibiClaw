package com.chibiclaw.voice.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AudioTrackManager — streaming PCM playback untuk TTS output.
 *
 * Phase 2: 22050 Hz mono PCM 16-bit (ElevenLabs default streaming format).
 * Tidak ducking media app lain (USAGE_VOICE_COMMUNICATION) supaya Spotify
 * tetap kedengaran tipis saat Fuu speak.
 *
 * Single-instance enforced via mutex — kalau ada call kedua saat playback,
 * dia wait.
 */
@Singleton
class AudioTrackManager @Inject constructor() {

    private var track: AudioTrack? = null
    private val mutex = Mutex()

    /**
     * Open streaming session. Caller wajib panggil [write] berulang lalu [close].
     */
    suspend fun open(sampleRate: Int = 22050) = mutex.withLock {
        check(track == null) { "AudioTrack already open" }

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        t.play()
        track = t
        Timber.d("AudioTrack opened @ ${sampleRate}Hz mono PCM16")
    }

    fun write(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Int {
        val t = track ?: return 0
        return t.write(buffer, offset, length, AudioTrack.WRITE_BLOCKING)
    }

    suspend fun close() = mutex.withLock {
        track?.let { t ->
            runCatching { t.stop() }
            runCatching { t.release() }
        }
        track = null
        Timber.d("AudioTrack closed")
    }

    fun isOpen(): Boolean = track != null
}
