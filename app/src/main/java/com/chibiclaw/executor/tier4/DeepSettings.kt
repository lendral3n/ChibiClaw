package com.chibiclaw.executor.tier4

import android.provider.Settings
import com.chibiclaw.executor.ShizukuAction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 5.2 — high-level facade for deep Android settings that normally
 * require `WRITE_SECURE_SETTINGS`, channelled through [ShizukuExecutor].
 *
 * Every "Skill JSON" that touches developer-flag territory (USB debugging,
 * adb Wi-Fi, animation scales, etc.) goes through one of the named
 * methods below — they embed the exact `Settings.*` key so Gemma never
 * sees the raw `put_secure` shell string.
 *
 * Each method returns a plain result string like `"ok:accessibility_enabled=1"`
 * or `"error:...<reason>..."` so the orchestrator's observation loop can
 * decide whether to retry.
 */
@Singleton
class DeepSettings @Inject constructor(
    private val shizuku: ShizukuExecutor
) {

    // ---- secure (Settings.Secure) ----

    suspend fun setAccessibilityServices(value: String): String =
        put("secure", Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, value)

    suspend fun setAccessibilityEnabled(enabled: Boolean): String =
        put("secure", Settings.Secure.ACCESSIBILITY_ENABLED, if (enabled) "1" else "0")

    suspend fun setLocationProvidersAllowed(value: String): String =
        put("secure", "location_providers_allowed", value)

    suspend fun setAdbEnabled(enabled: Boolean): String =
        put("global", Settings.Global.ADB_ENABLED, if (enabled) "1" else "0")

    suspend fun setAdbWifiEnabled(enabled: Boolean): String =
        put("global", "adb_wifi_enabled", if (enabled) "1" else "0")

    suspend fun setUsbDebugging(enabled: Boolean): String = setAdbEnabled(enabled)

    // ---- global (Settings.Global) ----

    suspend fun setAirplaneMode(enabled: Boolean): String = put(
        "global", Settings.Global.AIRPLANE_MODE_ON, if (enabled) "1" else "0"
    )

    suspend fun setAutoRotate(enabled: Boolean): String = put(
        "system", Settings.System.ACCELEROMETER_ROTATION, if (enabled) "1" else "0"
    )

    suspend fun setWindowAnimationScale(scale: Float): String =
        put("global", Settings.Global.WINDOW_ANIMATION_SCALE, scale.toString())

    suspend fun setTransitionAnimationScale(scale: Float): String =
        put("global", Settings.Global.TRANSITION_ANIMATION_SCALE, scale.toString())

    suspend fun setAnimatorDurationScale(scale: Float): String =
        put("global", Settings.Global.ANIMATOR_DURATION_SCALE, scale.toString())

    // ---- system (Settings.System) ----

    suspend fun setScreenBrightness(value: Int): String =
        put("system", Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(0, 255).toString())

    suspend fun setScreenBrightnessMode(automatic: Boolean): String =
        put(
            "system", Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (automatic) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC.toString()
            else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL.toString()
        )

    suspend fun setScreenOffTimeout(ms: Int): String =
        put("system", Settings.System.SCREEN_OFF_TIMEOUT, ms.coerceAtLeast(5_000).toString())

    suspend fun setRingVolume(value: Int): String =
        put("system", "volume_ring", value.toString())

    // ---- generic passthrough ----

    /**
     * Escape hatch for settings keys we haven't named yet. The skill
     * JSON is expected to validate the key on its own side; we still
     * re-validate via the whitelist regex inside ShizukuExecutor.
     */
    suspend fun putRaw(namespace: String, key: String, value: String): String =
        put(namespace, key, value)

    suspend fun getSecure(key: String): String = get("secure", key)
    suspend fun getGlobal(key: String): String = get("global", key)
    suspend fun getSystem(key: String): String = get("system", key)

    // ---- internals ----

    private suspend fun put(ns: String, key: String, value: String): String {
        val kind = when (ns) {
            "secure" -> "put_secure"
            "global" -> "put_global"
            "system" -> "put_system"
            else -> return "deep_settings_error: bad_namespace=$ns"
        }
        val result = shizuku.executeAction(ShizukuAction(kind = kind, payload = "$key $value"))
        return if (result.contains("rejected") || result.contains("error")) {
            "deep_settings_error: $result"
        } else {
            "ok:$key=$value"
        }
    }

    private suspend fun get(ns: String, key: String): String {
        val kind = when (ns) {
            "secure" -> "get_secure"
            "global" -> "get_global"
            "system" -> "get_system"
            else -> return "deep_settings_error: bad_namespace=$ns"
        }
        return shizuku.executeAction(ShizukuAction(kind = kind, payload = key))
    }
}
