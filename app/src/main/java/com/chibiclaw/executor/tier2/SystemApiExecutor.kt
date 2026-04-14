package com.chibiclaw.executor.tier2

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles system-level toggles that can NOT be fired via a normal Android
 * Intent: flashlight (CameraManager), volume (AudioManager), brightness
 * (Settings.System), and read-only wifi/bluetooth state.
 *
 * Write access to wifi and bluetooth toggles is blocked on Android 10+ for
 * normal apps, so those methods simply open the appropriate Settings page via
 * IntentExecutor in the caller.
 */
@Singleton
class SystemApiExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private val cameraManager: CameraManager? =
        context.getSystemService(CameraManager::class.java)

    // Track torch state ourselves — CameraManager has no direct query.
    @Volatile private var flashlightOn: Boolean = false

    /**
     * Central dispatcher invoked by ExecutionRouter for SystemControlAction.
     * `target` is normalized to lowercase; `state` is "on"/"off"/"toggle" or a
     * 0–100 integer for volume/brightness.
     */
    fun control(target: String, state: String): String {
        val t = target.trim().lowercase()
        val s = state.trim().lowercase()
        return when (t) {
            "flashlight", "torch", "senter", "flash" -> handleFlashlight(s)
            "volume", "suara" -> handleVolume(s)
            "brightness", "kecerahan" -> handleBrightness(s)
            "wifi", "wi-fi" -> "wifi_requires_settings_page"
            "bluetooth", "bt" -> "bluetooth_requires_settings_page"
            else -> "unknown_target: $target"
        }
    }

    private fun handleFlashlight(state: String): String {
        val desired = when (state) {
            "on", "1", "true", "nyala", "hidup", "aktif" -> true
            "off", "0", "false", "mati", "padam", "nonaktif" -> false
            "toggle", "ganti" -> !flashlightOn
            else -> return "flashlight_error: unknown state '$state' (use on/off/toggle)"
        }
        return setFlashlight(desired)
    }

    @SuppressLint("MissingPermission")
    fun setFlashlight(on: Boolean): String {
        val cm = cameraManager ?: return "flashlight_error: CameraManager unavailable"
        return try {
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "flashlight_error: no camera with flash"
            cm.setTorchMode(cameraId, on)
            flashlightOn = on
            Log.d(TAG, "Flashlight ${if (on) "ON" else "OFF"} (camera $cameraId)")
            if (on) "flashlight_on" else "flashlight_off"
        } catch (e: SecurityException) {
            "flashlight_error: camera permission missing"
        } catch (e: Exception) {
            Log.e(TAG, "Flashlight toggle failed: ${e.message}")
            "flashlight_error: ${e.message}"
        }
    }

    private fun handleVolume(state: String): String {
        val level = when (state) {
            "on", "max", "keras", "kencang" -> 100
            "off", "mute", "silent", "sunyi" -> 0
            else -> state.toIntOrNull() ?: return "volume_error: unknown state '$state'"
        }
        return setVolume(level)
    }

    fun setVolume(level: Int): String {
        val am = audioManager ?: return "volume_error: AudioManager unavailable"
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val clamped = level.coerceIn(0, 100)
        val actual = (clamped / 100f * max).toInt()
        am.setStreamVolume(AudioManager.STREAM_MUSIC, actual, 0)
        return "volume_set_${clamped}"
    }

    private fun handleBrightness(state: String): String {
        val level = when (state) {
            "max", "terang" -> 100
            "min", "redup" -> 10
            else -> state.toIntOrNull() ?: return "brightness_error: unknown state '$state'"
        }
        return setBrightness(level)
    }

    fun setBrightness(level: Int): String {
        // BUG-F: Settings.System.putInt() throws SecurityException when
        // WRITE_SETTINGS is missing, which on most OEMs (including
        // HyperOS 3.0) requires the user to toggle a per-app permission
        // in the "Modify system settings" screen. Checking upfront with
        // canWrite() lets us return an actionable error message instead
        // of a generic exception.
        if (!Settings.System.canWrite(context)) {
            Log.w(TAG, "WRITE_SETTINGS not granted — cannot change brightness")
            return "brightness_error: izin WRITE_SETTINGS belum diberikan. " +
                "Buka Settings → Apps → ChibiClaw → Modify system settings → aktifkan, lalu coba lagi."
        }
        return try {
            val clamped = level.coerceIn(0, 100)
            val actual = (clamped / 100f * 255).toInt()
            // Also disable adaptive brightness so the value sticks.
            try {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
            } catch (_: Exception) {
                // Non-fatal — some ROMs protect this key even when they
                // allow SCREEN_BRIGHTNESS writes.
            }
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                actual
            )
            "brightness_set_${clamped}"
        } catch (e: SecurityException) {
            Log.w(TAG, "Brightness SecurityException: ${e.message}")
            "brightness_error: izin ditolak (${e.message})"
        } catch (e: Exception) {
            Log.w(TAG, "Brightness write failed: ${e.message}")
            "brightness_error: ${e.message}"
        }
    }

    fun getSystemInfo(): String {
        val parts = mutableListOf<String>()

        // ── Volume ──
        val am = audioManager
        val volPct = if (am != null) {
            val vol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            (vol * 100f / maxVol).toInt()
        } else -1
        parts += "Volume: $volPct%"

        // ── WiFi ──
        val wifiEnabled = wifiManager?.isWifiEnabled ?: false
        parts += "WiFi: ${if (wifiEnabled) "ON" else "OFF"}"

        // ── Bluetooth ──
        val btEnabled = bluetoothAdapter?.isEnabled ?: false
        parts += "Bluetooth: ${if (btEnabled) "ON" else "OFF"}"

        // ── Flashlight ──
        parts += "Flashlight: ${if (flashlightOn) "ON" else "OFF"}"

        // ── Battery ──
        try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }
            if (batteryStatus != null) {
                val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val batteryPct = if (scale > 0) (level * 100 / scale) else -1

                val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val charging = when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                    BatteryManager.BATTERY_STATUS_FULL -> "Full"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                    else -> "Unknown"
                }

                val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                val plugType = when (plugged) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                    else -> "Unplugged"
                }

                val health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                val healthStr = when (health) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                    BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                    else -> "Unknown"
                }

                val tempRaw = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                val tempC = if (tempRaw > 0) tempRaw / 10f else -1f

                parts += "Battery: $batteryPct%"
                parts += "Battery Status: $charging ($plugType)"
                parts += "Battery Health: $healthStr"
                if (tempC > 0) parts += "Battery Temp: ${String.format("%.1f", tempC)}°C"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Battery info query failed: ${e.message}")
            parts += "Battery: unavailable"
        }

        // ── RAM ──
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager != null) {
                val memInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                val totalGB = memInfo.totalMem / (1024.0 * 1024 * 1024)
                val availGB = memInfo.availMem / (1024.0 * 1024 * 1024)
                val usedGB = totalGB - availGB
                val usedPct = ((usedGB / totalGB) * 100).toInt()
                parts += "RAM: ${String.format("%.1f", usedGB)}/${String.format("%.1f", totalGB)} GB ($usedPct% used)"
            }
        } catch (e: Exception) {
            Log.w(TAG, "RAM info query failed: ${e.message}")
        }

        // ── Storage ──
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.totalBytes
            val freeBytes = stat.availableBytes
            val usedBytes = totalBytes - freeBytes
            val totalGB = totalBytes / (1024.0 * 1024 * 1024)
            val usedGB = usedBytes / (1024.0 * 1024 * 1024)
            val freeGB = freeBytes / (1024.0 * 1024 * 1024)
            parts += "Storage: ${String.format("%.1f", usedGB)}/${String.format("%.1f", totalGB)} GB (${String.format("%.1f", freeGB)} GB free)"
        } catch (e: Exception) {
            Log.w(TAG, "Storage info query failed: ${e.message}")
        }

        // ── Brightness ──
        try {
            val brightnessVal = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                -1
            )
            if (brightnessVal >= 0) {
                val brightnessPct = (brightnessVal * 100f / 255).toInt()
                parts += "Brightness: $brightnessPct%"
            }
        } catch (e: Exception) {
            // Ignore
        }

        return parts.joinToString(" | ")
    }

    companion object {
        private const val TAG = "SystemApiExecutor"
    }
}
