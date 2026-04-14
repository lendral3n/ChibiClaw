package com.chibiclaw.executor.tier2

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.chibiclaw.executor.tier4.DeepSettings
import com.chibiclaw.service.ChibiAccessibility
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 11 — Display control: dark mode, night light, AOD, DPI, screenshot, screen recording.
 */
@Singleton
class DisplayControlExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deepSettings: DeepSettings
) {
    private val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager

    suspend fun perform(target: String, command: String, value: String): String {
        val t = target.trim().lowercase()
        val cmd = command.trim().lowercase()
        return when (t) {
            "dark_mode", "darkmode", "dark", "tema_gelap" -> handleDarkMode(cmd)
            "night_light", "nightlight", "blue_light", "eye_comfort" -> handleNightLight(cmd)
            "aod", "always_on_display", "always_on" -> handleAod(cmd)
            "dpi", "density" -> handleDpi(cmd, value)
            "font_size", "fontsize", "font" -> handleFontSize(cmd, value)
            "screenshot", "ss" -> takeScreenshot()
            "screen_record", "screenrecord", "record" -> handleScreenRecord(cmd)
            else -> "display_error: unknown target '$t'"
        }
    }

    private fun handleDarkMode(cmd: String): String {
        return try {
            when (cmd) {
                "on", "enable" -> {
                    uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)
                    "dark_mode_on"
                }
                "off", "disable" -> {
                    uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO)
                    "dark_mode_off"
                }
                "auto" -> {
                    uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO)
                    "dark_mode_auto"
                }
                "get", "status" -> {
                    val mode = uiModeManager.nightMode
                    val label = when (mode) {
                        UiModeManager.MODE_NIGHT_YES -> "ON"
                        UiModeManager.MODE_NIGHT_NO -> "OFF"
                        UiModeManager.MODE_NIGHT_AUTO -> "Auto"
                        else -> "Unknown"
                    }
                    "dark_mode: $label"
                }
                else -> "dark_mode_error: unknown command '$cmd'"
            }
        } catch (e: Exception) {
            "dark_mode_error: ${e.message}"
        }
    }

    private suspend fun handleNightLight(cmd: String): String {
        return when (cmd) {
            "on" -> deepSettings.putRaw("secure", "night_display_activated", "1")
            "off" -> deepSettings.putRaw("secure", "night_display_activated", "0")
            "get" -> deepSettings.getSecure("night_display_activated")
            else -> "night_light_error: unknown command '$cmd' (needs Shizuku)"
        }
    }

    private suspend fun handleAod(cmd: String): String {
        return when (cmd) {
            "on" -> deepSettings.putRaw("secure", "doze_always_on", "1")
            "off" -> deepSettings.putRaw("secure", "doze_always_on", "0")
            "get" -> deepSettings.getSecure("doze_always_on")
            else -> "aod_error: unknown command '$cmd' (needs Shizuku)"
        }
    }

    private suspend fun handleDpi(cmd: String, value: String): String {
        return when (cmd) {
            "set" -> {
                val dpi = value.toIntOrNull()?.coerceIn(200, 640) ?: return "dpi_error: invalid value '$value'"
                deepSettings.putRaw("secure", "display_density_forced", dpi.toString())
            }
            "reset" -> deepSettings.putRaw("secure", "display_density_forced", "")
            "get" -> {
                val density = context.resources.displayMetrics.densityDpi
                "dpi: $density"
            }
            else -> "dpi_error: unknown command '$cmd' (needs Shizuku)"
        }
    }

    private suspend fun handleFontSize(cmd: String, value: String): String {
        return when (cmd) {
            "set" -> {
                val scale = value.toFloatOrNull()?.coerceIn(0.5f, 2.0f) ?: return "font_error: invalid value '$value'"
                deepSettings.putRaw("system", "font_scale", scale.toString())
            }
            "get" -> {
                val scale = context.resources.configuration.fontScale
                "font_scale: $scale"
            }
            else -> "font_error: unknown command '$cmd'"
        }
    }

    private fun takeScreenshot(): String {
        val service = ChibiAccessibility.getInstance() ?: return "screenshot_error: accessibility not connected"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                "screenshot_taken"
            } else {
                "screenshot_error: requires Android 9+"
            }
        } catch (e: Exception) {
            "screenshot_error: ${e.message}"
        }
    }

    private fun handleScreenRecord(cmd: String): String {
        // Screen recording requires MediaProjection which needs user consent dialog
        // For now, open the built-in screen recorder if available
        return when (cmd) {
            "start" -> "screen_record_info: gunakan panel Quick Settings atau bilang 'buka screen recorder'"
            "stop" -> "screen_record_info: swipe down Quick Settings → tap stop recording"
            else -> "screen_record_error: unknown command '$cmd'"
        }
    }

    companion object {
        private const val TAG = "DisplayControlExecutor"
    }
}
