package com.chibiclaw.executor.tier2

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import com.chibiclaw.executor.tier4.DeepSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 11 — Hardware control: IR blaster, NFC, vibration, rotation, screen timeout, refresh rate.
 */
@Singleton
class HardwareControlExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deepSettings: DeepSettings
) {
    private val irManager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    suspend fun perform(target: String, command: String, value: String): String {
        val t = target.trim().lowercase()
        val cmd = command.trim().lowercase()
        return when (t) {
            "ir", "ir_blaster", "infrared", "remote" -> handleIr(cmd, value)
            "nfc" -> handleNfc(cmd)
            "vibrate", "vibration", "haptic", "getar" -> handleVibrate(cmd, value)
            "rotation", "auto_rotate", "rotate" -> handleRotation(cmd)
            "screen_timeout", "timeout", "screen_off" -> handleScreenTimeout(cmd, value)
            "refresh_rate", "hz" -> handleRefreshRate(cmd, value)
            else -> "hardware_error: unknown target '$t'"
        }
    }

    private fun handleIr(cmd: String, value: String): String {
        val ir = irManager ?: return "ir_error: device has no IR blaster"
        if (!ir.hasIrEmitter()) return "ir_error: IR emitter not available"
        return when (cmd) {
            "check", "status", "get" -> {
                val ranges = ir.carrierFrequencies
                "ir_available: ${ranges.size} frequency ranges supported"
            }
            "send", "transmit" -> {
                // value format: "freq:pattern" e.g. "38000:100,50,100,50"
                val parts = value.split(":", limit = 2)
                if (parts.size != 2) return "ir_error: format 'freq:on,off,on,off' e.g. '38000:100,50,100,50'"
                val freq = parts[0].toIntOrNull() ?: return "ir_error: invalid frequency"
                val pattern = parts[1].split(",").mapNotNull { it.trim().toIntOrNull() }.toIntArray()
                if (pattern.isEmpty()) return "ir_error: empty pattern"
                try {
                    ir.transmit(freq, pattern)
                    "ir_sent: freq=$freq pattern_length=${pattern.size}"
                } catch (e: Exception) {
                    "ir_error: ${e.message}"
                }
            }
            // Common TV/AC remote presets
            "tv_power" -> sendPresetIr(38000, intArrayOf(9000,4500,560,560,560,560,560,1690,560,560,560,560,560,560,560,560,560,560,560,1690,560,1690,560,560,560,1690,560,1690,560,1690,560,1690,560,1690,560,560,560,1690,560,560,560,560,560,560,560,560,560,560,560,560,560,1690,560,560,560,1690,560,1690,560,1690,560,1690,560,1690,560,1690,560,43000))
            else -> "ir_error: unknown command '$cmd'"
        }
    }

    private fun sendPresetIr(freq: Int, pattern: IntArray): String {
        val ir = irManager ?: return "ir_error: no IR"
        return try {
            ir.transmit(freq, pattern)
            "ir_sent_preset: freq=$freq"
        } catch (e: Exception) {
            "ir_error: ${e.message}"
        }
    }

    private suspend fun handleNfc(cmd: String): String {
        return when (cmd) {
            "on" -> deepSettings.putRaw("secure", "nfc_on", "1")
            "off" -> deepSettings.putRaw("secure", "nfc_on", "0")
            "get", "status" -> deepSettings.getSecure("nfc_on")
            else -> "nfc_error: unknown command '$cmd' (needs Shizuku)"
        }
    }

    private fun handleVibrate(cmd: String, value: String): String {
        return try {
            when (cmd) {
                "short", "tap" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION") vibrator.vibrate(100)
                    }
                    "vibrate_short"
                }
                "long" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION") vibrator.vibrate(500)
                    }
                    "vibrate_long"
                }
                "pattern" -> {
                    // value = "0,100,200,300" (off,on,off,on...)
                    val pattern = value.split(",").mapNotNull { it.trim().toLongOrNull() }.toLongArray()
                    if (pattern.isEmpty()) return "vibrate_error: invalid pattern"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } else {
                        @Suppress("DEPRECATION") vibrator.vibrate(pattern, -1)
                    }
                    "vibrate_pattern: ${pattern.size} segments"
                }
                "stop", "cancel" -> {
                    vibrator.cancel()
                    "vibrate_stopped"
                }
                "check", "status", "get" -> {
                    "vibrator: hasVibrator=${vibrator.hasVibrator()}"
                }
                else -> {
                    // Default: vibrate for given ms
                    val ms = value.toLongOrNull() ?: cmd.toLongOrNull() ?: 200L
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(ms.coerceIn(50, 5000), VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION") vibrator.vibrate(ms.coerceIn(50, 5000))
                    }
                    "vibrate: ${ms}ms"
                }
            }
        } catch (e: Exception) {
            "vibrate_error: ${e.message}"
        }
    }

    private suspend fun handleRotation(cmd: String): String {
        return when (cmd) {
            "on", "auto" -> {
                try {
                    Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
                    "rotation_auto_on"
                } catch (e: SecurityException) {
                    deepSettings.setAutoRotate(true)
                }
            }
            "off" -> {
                try {
                    Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                    "rotation_auto_off"
                } catch (e: SecurityException) {
                    deepSettings.setAutoRotate(false)
                }
            }
            "portrait" -> {
                try {
                    Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                    Settings.System.putInt(context.contentResolver, Settings.System.USER_ROTATION, 0)
                    "rotation_portrait"
                } catch (e: SecurityException) {
                    "rotation_error: WRITE_SETTINGS not granted"
                }
            }
            "landscape" -> {
                try {
                    Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                    Settings.System.putInt(context.contentResolver, Settings.System.USER_ROTATION, 1)
                    "rotation_landscape"
                } catch (e: SecurityException) {
                    "rotation_error: WRITE_SETTINGS not granted"
                }
            }
            "get", "status" -> {
                val autoRotate = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                "rotation: auto=${autoRotate == 1}"
            }
            else -> "rotation_error: unknown command '$cmd'"
        }
    }

    private suspend fun handleScreenTimeout(cmd: String, value: String): String {
        return when (cmd) {
            "set" -> {
                val ms = when (value.lowercase()) {
                    "15s" -> 15000; "30s" -> 30000; "1m", "1min" -> 60000
                    "2m", "2min" -> 120000; "5m", "5min" -> 300000
                    "10m", "10min" -> 600000; "30m", "30min" -> 1800000
                    "never" -> Int.MAX_VALUE
                    else -> value.toIntOrNull() ?: return "timeout_error: invalid value '$value'"
                }
                deepSettings.setScreenOffTimeout(ms)
            }
            "get", "status" -> {
                val ms = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30000)
                val label = when {
                    ms >= Int.MAX_VALUE -> "Never"
                    ms >= 60000 -> "${ms / 60000}m"
                    else -> "${ms / 1000}s"
                }
                "screen_timeout: $label ($ms ms)"
            }
            else -> "timeout_error: unknown command '$cmd'"
        }
    }

    private suspend fun handleRefreshRate(cmd: String, value: String): String {
        return when (cmd) {
            "set" -> {
                val hz = value.replace("hz", "", ignoreCase = true).trim()
                deepSettings.putRaw("system", "peak_refresh_rate", "${hz}.0")
                deepSettings.putRaw("system", "min_refresh_rate", "${hz}.0")
            }
            "get", "status" -> {
                val display = context.resources.displayMetrics
                "refresh_rate: check Settings → Display → Refresh rate"
            }
            else -> "refresh_rate_error: unknown command '$cmd' (needs Shizuku)"
        }
    }

    companion object {
        private const val TAG = "HardwareControlExecutor"
    }
}
