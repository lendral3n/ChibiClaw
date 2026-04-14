package com.chibiclaw.perception

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.chibiclaw.service.ChibiAccessibility
import com.chibiclaw.service.NotificationListener
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 9.2 — permission probe.
 *
 * Single source of truth for "what can Chibi actually do right now?".
 * Every feature screen calls into here instead of hand-rolling its
 * own ContextCompat.checkSelfPermission() spaghetti — that way if
 * the user grants/revokes something at runtime we always see it.
 *
 * The class also exposes the *Intent* needed to open the matching
 * settings page, so the Permission Wizard UI can render a "Grant"
 * button next to each missing item.
 */
@Singleton
class PermissionStatus @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class PermissionEntry(
        val id: String,
        val label: String,
        val required: Boolean,
        val granted: Boolean,
        val grantIntent: Intent?
    )

    fun snapshot(): List<PermissionEntry> = listOf(
        PermissionEntry(
            id = "accessibility",
            label = "Accessibility Service (UI control)",
            required = true,
            granted = isAccessibilityEnabled(),
            grantIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        ),
        PermissionEntry(
            id = "notification_listener",
            label = "Notification Access (baca/cancel notifikasi)",
            required = true,
            granted = isNotificationListenerEnabled(),
            grantIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        ),
        PermissionEntry(
            id = "overlay",
            label = "Display over other apps (floating bubble)",
            required = true,
            granted = canDrawOverlays(),
            grantIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        ),
        PermissionEntry(
            id = "usage_stats",
            label = "Usage Access (deteksi app foreground)",
            required = false,
            granted = isUsageAccessGranted(),
            grantIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        ),
        PermissionEntry(
            id = "post_notifications",
            label = "Post notifications",
            required = true,
            granted = isRuntimeGranted("android.permission.POST_NOTIFICATIONS"),
            grantIntent = appSettingsIntent()
        ),
        PermissionEntry(
            id = "record_audio",
            label = "Microphone (voice command)",
            required = false,
            granted = isRuntimeGranted(Manifest.permission.RECORD_AUDIO),
            grantIntent = appSettingsIntent()
        ),
        PermissionEntry(
            id = "camera",
            label = "Camera (vision / QR / OCR gallery)",
            required = false,
            granted = isRuntimeGranted(Manifest.permission.CAMERA),
            grantIntent = appSettingsIntent()
        ),
        PermissionEntry(
            id = "location_background",
            label = "Background Location (geofence triggers)",
            required = false,
            granted = isRuntimeGranted(Manifest.permission.ACCESS_FINE_LOCATION) &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                 isRuntimeGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)),
            grantIntent = appSettingsIntent()
        ),
        PermissionEntry(
            id = "sms",
            label = "SMS (baca & kirim)",
            required = false,
            granted = isRuntimeGranted(Manifest.permission.SEND_SMS) &&
                isRuntimeGranted(Manifest.permission.READ_SMS),
            grantIntent = appSettingsIntent()
        ),
        PermissionEntry(
            id = "contacts",
            label = "Contacts",
            required = false,
            granted = isRuntimeGranted(Manifest.permission.READ_CONTACTS),
            grantIntent = appSettingsIntent()
        ),
        PermissionEntry(
            id = "calendar",
            label = "Calendar",
            required = false,
            granted = isRuntimeGranted(Manifest.permission.READ_CALENDAR),
            grantIntent = appSettingsIntent()
        ),
        PermissionEntry(
            id = "write_settings",
            label = "WRITE_SETTINGS (brightness etc.)",
            required = false,
            granted = canWriteSettings(),
            grantIntent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        )
    )

    fun missingRequired(): List<PermissionEntry> =
        snapshot().filter { it.required && !it.granted }

    fun allRequiredGranted(): Boolean = missingRequired().isEmpty()

    // ---- probes ----

    private fun isRuntimeGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityEnabled(): Boolean {
        if (ChibiAccessibility.getInstance() != null) return true
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = "${context.packageName}/${ChibiAccessibility::class.java.name}"
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(target, ignoreCase = true)) return true
        }
        return false
    }

    private fun isNotificationListenerEnabled(): Boolean {
        // Prefer our live singleton (cheaper + more accurate).
        if (NotificationListener.getInstance() != null) return true
        val flat = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        val me = ComponentName(context, NotificationListener::class.java).flattenToString()
        return flat.split(":").any { it == me }
    }

    private fun canDrawOverlays(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return Settings.canDrawOverlays(context)
    }

    private fun canWriteSettings(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return Settings.System.canWrite(context)
    }

    private fun isUsageAccessGranted(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    private fun appSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
}
