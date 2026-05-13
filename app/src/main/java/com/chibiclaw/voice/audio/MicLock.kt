package com.chibiclaw.voice.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MicLock — global mutex untuk mic acquisition + AudioFocus coordination.
 *
 * Hanya 1 task atau component yang bisa hold mic dalam satu waktu. Kalau ada
 * komponen lain (Google Assistant, voice recorder app) yang mau pakai mic,
 * AudioFocusChangeListener akan trigger release.
 *
 * Pattern referensi: docs/architecture/15-voice-pipeline.md section Mic Conflict.
 */
@Singleton
class MicLock @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mutex = Mutex()

    /**
     * Acquire mic + audio focus, jalankan block, lalu release.
     */
    suspend fun <T> withMic(block: suspend () -> T): T = mutex.withLock {
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        Timber.d("Mic focus lost: $focusChange (Phase 2 simple — Phase 8 cancel ongoing record)")
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        Timber.d("Mic focus regained")
                    }
                }
            }
            .build()

        val granted = audioManager.requestAudioFocus(focusRequest) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) {
            throw IllegalStateException("Audio focus denied — Google Assistant atau app lain pakai mic")
        }
        try {
            return@withLock block()
        } finally {
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }
}
