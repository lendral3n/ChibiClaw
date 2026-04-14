package com.chibiclaw.executor.tier2

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent
import com.chibiclaw.executor.MediaSessionAction
import com.chibiclaw.service.NotificationListener
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4.6 — global media transport control without the signature-only
 * MEDIA_CONTENT_CONTROL permission by piggybacking on our existing
 * NotificationListenerService. MediaSessionManager.getActiveSessions()
 * accepts a ComponentName tied to a bound NotificationListener, so we
 * don't need any extra permission flip in Settings.
 *
 * Commands: play, pause, toggle, next, prev, stop, volume_up, volume_down,
 *           mute, info.
 *
 * "info" returns the currently-playing track (title • artist) so Gemma
 * can quote it back to the user.
 */
@Singleton
class MediaSessionExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager: MediaSessionManager? by lazy {
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    }
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    suspend fun perform(action: MediaSessionAction): String {
        val cmd = action.command.lowercase()
        return when (cmd) {
            "volume_up" -> adjustVolume(AudioManager.ADJUST_RAISE, "vol_up")
            "volume_down" -> adjustVolume(AudioManager.ADJUST_LOWER, "vol_down")
            "mute" -> adjustVolume(AudioManager.ADJUST_MUTE, "mute")
            "unmute" -> adjustVolume(AudioManager.ADJUST_UNMUTE, "unmute")
            else -> dispatchToActiveSession(cmd)
        }
    }

    private fun adjustVolume(direction: Int, label: String): String {
        return try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            "media_$label"
        } catch (e: Exception) {
            "media_error: ${e.message}"
        }
    }

    private fun dispatchToActiveSession(cmd: String): String {
        val mgr = manager ?: return "media_error: manager_unavailable"
        val listenerComponent = ComponentName(context, NotificationListener::class.java)
        val controllers = try {
            mgr.getActiveSessions(listenerComponent)
        } catch (e: SecurityException) {
            return "media_error: notification_listener_not_granted"
        } catch (e: Exception) {
            return "media_error: ${e.message}"
        }

        if (controllers.isEmpty()) return "media_error: no_active_session"
        val target = pickActive(controllers)

        return when (cmd) {
            "info", "now_playing" -> describe(target)
            "play" -> { target.transportControls.play(); "media_play" }
            "pause" -> { target.transportControls.pause(); "media_pause" }
            "toggle" -> {
                val state = target.playbackState?.state ?: PlaybackState.STATE_NONE
                if (state == PlaybackState.STATE_PLAYING) {
                    target.transportControls.pause(); "media_paused"
                } else {
                    target.transportControls.play(); "media_resumed"
                }
            }
            "next", "skip" -> { target.transportControls.skipToNext(); "media_next" }
            "prev", "previous" -> { target.transportControls.skipToPrevious(); "media_prev" }
            "stop" -> { target.transportControls.stop(); "media_stop" }
            "ff", "fast_forward" -> { target.transportControls.fastForward(); "media_ff" }
            "rw", "rewind" -> { target.transportControls.rewind(); "media_rw" }
            else -> "media_error: unknown_cmd=$cmd"
        }
    }

    private fun pickActive(controllers: List<MediaController>): MediaController {
        // Prefer the one that is currently PLAYING; otherwise return the
        // first (most recently active) session.
        return controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.first()
    }

    private fun describe(controller: MediaController): String {
        val md = controller.metadata ?: return "media_info: no_metadata"
        val title = md.getString(android.media.MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = md.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val album = md.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        return "media_info: \"$title\" • $artist ($album) — pkg=${controller.packageName}"
    }

    // Key-event fallback used when no active session is available.
    @Suppress("unused")
    private fun dispatchKey(code: Int): String {
        return try {
            val down = KeyEvent(KeyEvent.ACTION_DOWN, code)
            val up = KeyEvent(KeyEvent.ACTION_UP, code)
            audioManager.dispatchMediaKeyEvent(down)
            audioManager.dispatchMediaKeyEvent(up)
            "media_key_$code"
        } catch (e: Exception) {
            "media_error: ${e.message}"
        }
    }
}
