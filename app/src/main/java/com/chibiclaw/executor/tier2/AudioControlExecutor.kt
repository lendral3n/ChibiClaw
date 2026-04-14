package com.chibiclaw.executor.tier2

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 11 — Audio control executor for all volume streams, DND, ringer mode, mic.
 */
@Singleton
class AudioControlExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun perform(target: String, command: String, value: String): String {
        val t = target.trim().lowercase()
        val cmd = command.trim().lowercase()
        return when (t) {
            "ring_volume", "ring", "ringtone" -> handleVolume(AudioManager.STREAM_RING, cmd, value, "ring")
            "notification_volume", "notif_volume", "notif" -> handleVolume(AudioManager.STREAM_NOTIFICATION, cmd, value, "notif")
            "alarm_volume", "alarm" -> handleVolume(AudioManager.STREAM_ALARM, cmd, value, "alarm")
            "call_volume", "call" -> handleVolume(AudioManager.STREAM_VOICE_CALL, cmd, value, "call")
            "system_volume", "system_sound" -> handleVolume(AudioManager.STREAM_SYSTEM, cmd, value, "system")
            "dnd", "do_not_disturb" -> handleDnd(cmd, value)
            "ringer_mode", "ringer", "mode" -> handleRingerMode(cmd, value)
            "mic", "microphone" -> handleMic(cmd)
            "speaker" -> handleSpeaker(cmd)
            else -> "audio_error: unknown target '$t'"
        }
    }

    private fun handleVolume(stream: Int, cmd: String, value: String, label: String): String {
        val max = audioManager.getStreamMaxVolume(stream)
        return when (cmd) {
            "get" -> {
                val cur = audioManager.getStreamVolume(stream)
                val pct = (cur * 100f / max).toInt()
                "audio_${label}_volume: $pct%"
            }
            "set" -> {
                val pct = value.toIntOrNull()?.coerceIn(0, 100) ?: return "audio_error: invalid value '$value'"
                val actual = (pct / 100f * max).toInt()
                audioManager.setStreamVolume(stream, actual, 0)
                "audio_${label}_set: $pct%"
            }
            "up" -> { audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, 0); "audio_${label}_up" }
            "down" -> { audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, 0); "audio_${label}_down" }
            "mute" -> { audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0); "audio_${label}_muted" }
            "unmute" -> { audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0); "audio_${label}_unmuted" }
            "max" -> { audioManager.setStreamVolume(stream, max, 0); "audio_${label}_max" }
            "min", "silent" -> { audioManager.setStreamVolume(stream, 0, 0); "audio_${label}_silent" }
            else -> "audio_error: unknown command '$cmd'"
        }
    }

    private fun handleDnd(cmd: String, value: String): String {
        if (!notifManager.isNotificationPolicyAccessGranted) {
            return "dnd_error: ACCESS_NOTIFICATION_POLICY not granted. Buka Settings → Apps → ChibiClaw → Notifications → DND access"
        }
        return try {
            when (cmd) {
                "on" -> {
                    val filter = when (value.lowercase()) {
                        "total", "total_silence" -> NotificationManager.INTERRUPTION_FILTER_NONE
                        "alarms", "alarm_only" -> NotificationManager.INTERRUPTION_FILTER_ALARMS
                        "priority" -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        else -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    }
                    notifManager.setInterruptionFilter(filter)
                    "dnd_on: $value"
                }
                "off" -> {
                    notifManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    "dnd_off"
                }
                "get", "status" -> {
                    val filter = notifManager.currentInterruptionFilter
                    val label = when (filter) {
                        NotificationManager.INTERRUPTION_FILTER_ALL -> "OFF"
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Priority Only"
                        NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Alarms Only"
                        NotificationManager.INTERRUPTION_FILTER_NONE -> "Total Silence"
                        else -> "Unknown"
                    }
                    "dnd_status: $label"
                }
                else -> "dnd_error: unknown command '$cmd'"
            }
        } catch (e: Exception) {
            "dnd_error: ${e.message}"
        }
    }

    private fun handleRingerMode(cmd: String, value: String): String {
        return try {
            when (cmd) {
                "get", "status" -> {
                    val mode = when (audioManager.ringerMode) {
                        AudioManager.RINGER_MODE_NORMAL -> "Normal"
                        AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                        AudioManager.RINGER_MODE_SILENT -> "Silent"
                        else -> "Unknown"
                    }
                    "ringer_mode: $mode"
                }
                "set" -> {
                    val mode = when (value.lowercase()) {
                        "normal", "ring" -> AudioManager.RINGER_MODE_NORMAL
                        "vibrate", "getar" -> AudioManager.RINGER_MODE_VIBRATE
                        "silent", "sunyi", "diam" -> AudioManager.RINGER_MODE_SILENT
                        else -> return "ringer_error: unknown mode '$value' (use normal/vibrate/silent)"
                    }
                    audioManager.ringerMode = mode
                    "ringer_set: $value"
                }
                else -> "ringer_error: unknown command '$cmd'"
            }
        } catch (e: Exception) {
            "ringer_error: ${e.message}"
        }
    }

    private fun handleMic(cmd: String): String {
        return try {
            when (cmd) {
                "mute", "off" -> { audioManager.isMicrophoneMute = true; "mic_muted" }
                "unmute", "on" -> { audioManager.isMicrophoneMute = false; "mic_unmuted" }
                "toggle" -> {
                    val newState = !audioManager.isMicrophoneMute
                    audioManager.isMicrophoneMute = newState
                    if (newState) "mic_muted" else "mic_unmuted"
                }
                "get", "status" -> if (audioManager.isMicrophoneMute) "mic: muted" else "mic: active"
                else -> "mic_error: unknown command '$cmd'"
            }
        } catch (e: Exception) {
            "mic_error: ${e.message}"
        }
    }

    private fun handleSpeaker(cmd: String): String {
        return try {
            when (cmd) {
                "on" -> { audioManager.isSpeakerphoneOn = true; "speaker_on" }
                "off" -> { audioManager.isSpeakerphoneOn = false; "speaker_off" }
                "toggle" -> {
                    val newState = !audioManager.isSpeakerphoneOn
                    audioManager.isSpeakerphoneOn = newState
                    if (newState) "speaker_on" else "speaker_off"
                }
                "get" -> if (audioManager.isSpeakerphoneOn) "speaker: on" else "speaker: off"
                else -> "speaker_error: unknown command '$cmd'"
            }
        } catch (e: Exception) {
            "speaker_error: ${e.message}"
        }
    }

    companion object {
        private const val TAG = "AudioControlExecutor"
    }
}
