package com.chibiclaw.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.chibiclaw.service.ChibiAccessibility
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper for opening the correct system setting pages so the user can
 * enable [ChibiAccessibility].
 *
 * Why this class exists:
 *   On Android 13+ Google introduced "Restricted Settings" (aka Enhanced
 *   Confirmation Mode). Apps installed outside the Play Store cannot be
 *   toggled in Settings → Accessibility until the user opens App Info and
 *   taps the hidden overflow menu → "Allow restricted settings". Until
 *   that menu is triggered once, the app does not even show up in the
 *   Accessibility list, which is exactly the symptom the user reported on
 *   HyperOS 3.0.
 *
 *   The overflow menu is itself only visible AFTER the user tries to
 *   enable the setting and hits the restricted-setting warning, so we
 *   guide the user through this in a specific order:
 *     1. Open the Accessibility settings page directly (user tries to
 *        flip the toggle → system shows "Restricted setting" dialog →
 *        this registers the app with Enhanced Confirmation Mode)
 *     2. Open App Info page (now the hidden ⋮ menu is visible — user
 *        taps it and selects "Allow restricted settings")
 *     3. Return to Accessibility settings and flip the toggle
 *
 *   On HyperOS/MIUI there is an extra layer: users must also enable
 *   "Install via USB" or "USB debugging (Security settings)" in Developer
 *   Options, which the user has already done, so we do not show that
 *   instruction here.
 */
@Singleton
class AccessibilityGuide @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** True if [ChibiAccessibility] is enabled and running. */
    fun isServiceEnabled(): Boolean {
        // Fast path: if the service is currently bound we already know.
        if (ChibiAccessibility.isConnected()) return true

        // Fallback: check the Settings.Secure enabled_accessibility_services
        // string for our component. This covers the case where the service
        // is enabled but has not yet been connected by the system (e.g. the
        // user just toggled it and we haven't received onServiceConnected).
        val expected = ComponentName(context, ChibiAccessibility::class.java)
            .flattenToString()
        return try {
            val am = context.getSystemService(AccessibilityManager::class.java)
            val enabled = am?.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).orEmpty()
            enabled.any {
                it.resolveInfo?.serviceInfo?.let { si ->
                    "${si.packageName}/${si.name}" == expected ||
                        "${si.packageName}/${si.name}".endsWith(".service.ChibiAccessibility")
                } == true
            } || run {
                // Final fallback — parse the raw setting string.
                val raw = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ).orEmpty()
                raw.split(':').any { it.equals(expected, ignoreCase = true) ||
                    it.endsWith("/.service.ChibiAccessibility") }
            }
        } catch (e: Exception) {
            Log.w(TAG, "isServiceEnabled check failed: ${e.message}")
            false
        }
    }

    /**
     * Opens Settings → Accessibility → Installed services. On Android 13+
     * tapping the ChibiClaw entry here will trigger the "Restricted setting"
     * dialog, which is needed before the App Info overflow menu becomes
     * visible.
     */
    fun openAccessibilitySettings(): Boolean = try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.e(TAG, "openAccessibilitySettings failed: ${e.message}")
        false
    }

    /**
     * Opens the App Info page for ChibiClaw. After the user has hit the
     * Restricted Setting dialog at least once, the overflow (⋮) menu in the
     * top-right of this screen will contain "Allow restricted settings".
     */
    fun openAppInfoPage(): Boolean = try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.e(TAG, "openAppInfoPage failed: ${e.message}")
        false
    }

    /** Whether the device is running Android 13+ and therefore subject to
     *  Restricted Settings / Enhanced Confirmation Mode. */
    fun isRestrictedSettingsPlatform(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    companion object {
        private const val TAG = "AccessibilityGuide"
    }
}
