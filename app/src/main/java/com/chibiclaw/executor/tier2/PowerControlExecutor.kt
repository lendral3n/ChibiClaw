package com.chibiclaw.executor.tier2

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.chibiclaw.executor.tier4.DeepSettings
import com.chibiclaw.executor.tier4.ShizukuExecutor
import com.chibiclaw.service.ChibiAccessibility
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 11 — Power control: battery saver, lock, wake, reboot, performance mode.
 */
@Singleton
class PowerControlExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deepSettings: DeepSettings,
    private val shizukuExecutor: ShizukuExecutor
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    suspend fun perform(target: String, command: String): String {
        val t = target.trim().lowercase()
        val cmd = command.trim().lowercase()
        return when (t) {
            "battery_saver", "hemat_baterai", "power_save" -> handleBatterySaver(cmd)
            "lock", "lock_screen", "kunci" -> lockScreen()
            "wake", "wake_screen", "nyalakan_layar" -> wakeScreen()
            "reboot", "restart" -> handleReboot(cmd)
            "performance", "performance_mode", "game_turbo" -> handlePerformance(cmd)
            "screen_on", "is_screen_on" -> {
                "screen: ${if (powerManager.isInteractive) "ON" else "OFF"}"
            }
            "battery_status", "baterai" -> getBatteryStatus()
            else -> "power_error: unknown target '$t'"
        }
    }

    private suspend fun handleBatterySaver(cmd: String): String {
        return when (cmd) {
            "on" -> {
                deepSettings.putRaw("global", "low_power", "1")
            }
            "off" -> {
                deepSettings.putRaw("global", "low_power", "0")
            }
            "get", "status" -> {
                "battery_saver: ${if (powerManager.isPowerSaveMode) "ON" else "OFF"}"
            }
            "toggle" -> {
                val newState = if (powerManager.isPowerSaveMode) "0" else "1"
                deepSettings.putRaw("global", "low_power", newState)
            }
            else -> "battery_saver_error: unknown command '$cmd'"
        }
    }

    private fun lockScreen(): String {
        val service = ChibiAccessibility.getInstance()
            ?: return "lock_error: accessibility not connected"
        return try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            "screen_locked"
        } catch (e: Exception) {
            "lock_error: ${e.message}"
        }
    }

    private fun wakeScreen(): String {
        return try {
            @Suppress("DEPRECATION")
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "ChibiClaw:WakeScreen"
            )
            wakeLock.acquire(3000L)
            wakeLock.release()
            "screen_woken"
        } catch (e: Exception) {
            "wake_error: ${e.message}"
        }
    }

    private suspend fun handleReboot(cmd: String): String {
        return when (cmd) {
            "normal", "reboot" -> shizukuExecutor.execute("reboot")
            "recovery" -> shizukuExecutor.execute("reboot recovery")
            "bootloader", "fastboot" -> shizukuExecutor.execute("reboot bootloader")
            "soft", "restart" -> shizukuExecutor.execute("stop && start")
            else -> shizukuExecutor.execute("reboot")
        }
    }

    private suspend fun handlePerformance(cmd: String): String {
        // Xiaomi-specific performance mode via Settings.Secure
        return when (cmd) {
            "on", "high" -> deepSettings.putRaw("secure", "performance_mode", "1")
            "off", "normal" -> deepSettings.putRaw("secure", "performance_mode", "0")
            "get", "status" -> deepSettings.getSecure("performance_mode")
            else -> "performance_error: unknown command '$cmd'"
        }
    }

    private fun getBatteryStatus(): String {
        val parts = mutableListOf<String>()
        try {
            val batteryIntent: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }
            if (batteryIntent != null) {
                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val pct = if (scale > 0) (level * 100 / scale) else -1
                val status = when (batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                    BatteryManager.BATTERY_STATUS_FULL -> "Full"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                    else -> "Unknown"
                }
                val plugged = when (batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                    else -> "Unplugged"
                }
                val health = when (batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                    else -> "Unknown"
                }
                val temp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10f
                parts += "Battery: $pct%"
                parts += "Status: $status ($plugged)"
                parts += "Health: $health"
                if (temp > 0) parts += "Temp: ${String.format("%.1f", temp)}°C"
                parts += "Power Save: ${if (powerManager.isPowerSaveMode) "ON" else "OFF"}"
            }
        } catch (e: Exception) {
            parts += "battery_error: ${e.message}"
        }
        return parts.joinToString(" | ")
    }
}
