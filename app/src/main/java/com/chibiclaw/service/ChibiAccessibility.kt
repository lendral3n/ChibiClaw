package com.chibiclaw.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat

/**
 * Phase 10 — hardened [AccessibilityService] with MIUI/HyperOS
 * watchdog.
 *
 * MIUI aggressively kills accessibility services for sideloaded apps
 * due to:
 *   1. Battery optimization (auto-disable after X hours)
 *   2. Enhanced Confirmation Mode (Android 13+ / API 33+)
 *   3. "Restricted Settings" flag on sideloaded apps
 *
 * Mitigations:
 *   - [onDestroy] fires a sticky notification telling the user to
 *     re-enable, with a one-tap intent to the Accessibility Settings.
 *   - [onServiceConnected] cancels any stale "re-enable" notification.
 *   - [connectionCallbacks] let ChibiService and Dashboard react
 *     immediately to connectivity changes without polling.
 */
class ChibiAccessibility : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info
        Log.d(TAG, "ChibiAccessibility connected")

        // Cancel any "re-enable" notification from a previous kill
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(WATCHDOG_NOTIF_ID)

        // Notify subscribers
        connectionCallbacks.forEach { it(true) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Events handled on-demand via getRootNode() — not streaming
    }

    override fun onInterrupt() {
        Log.w(TAG, "ChibiAccessibility interrupted")
    }

    override fun onDestroy() {
        Log.w(TAG, "ChibiAccessibility DESTROYED — MIUI may have killed the service")
        instance = null

        // Notify subscribers
        connectionCallbacks.forEach { it(false) }

        // Fire a notification so the user knows accessibility was killed
        // and can re-enable with one tap. This runs on a Handler because
        // the system might be tearing down our process and we need the
        // notification to post before we die.
        Handler(Looper.getMainLooper()).post {
            try {
                showReEnableNotification()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show watchdog notification: ${e.message}")
            }
        }

        super.onDestroy()
    }

    private fun showReEnableNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Ensure channel exists
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                WATCHDOG_CHANNEL_ID,
                "Accessibility Watchdog",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pemberitahuan saat accessibility service dimatikan sistem"
            }
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, WATCHDOG_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("ChibiClaw: Accessibility Mati")
            .setContentText("Ketuk untuk mengaktifkan ulang agar ChibiClaw bisa kontrol device.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Sistem mematikan accessibility ChibiClaw.\n\n" +
                        "Untuk mencegah ini terulang:\n" +
                        "1. Settings → Apps → ChibiClaw → Battery → No restrictions\n" +
                        "2. Settings → Apps → ChibiClaw → Autostart → Enable\n" +
                        "3. Lock ChibiClaw di Recent Apps (swipe down on card)"
                    )
            )
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(false)
            .build()

        nm.notify(WATCHDOG_NOTIF_ID, notif)
    }

    companion object {
        private const val TAG = "ChibiAccessibility"
        private const val WATCHDOG_NOTIF_ID = 9001
        private const val WATCHDOG_CHANNEL_ID = "accessibility_watchdog"

        @Volatile
        private var instance: ChibiAccessibility? = null

        /** Callbacks notified on connect/disconnect (true=connected). */
        private val connectionCallbacks = mutableListOf<(Boolean) -> Unit>()

        fun getInstance(): ChibiAccessibility? = instance

        fun isConnected(): Boolean = instance != null

        fun getRootNode(): AccessibilityNodeInfo? = instance?.rootInActiveWindow

        /**
         * Register a callback that fires whenever the accessibility service
         * connects or disconnects. Used by DashboardViewModel to show
         * a warning banner without polling.
         */
        fun addConnectionCallback(cb: (Boolean) -> Unit) {
            connectionCallbacks.add(cb)
        }

        fun removeConnectionCallback(cb: (Boolean) -> Unit) {
            connectionCallbacks.remove(cb)
        }
    }
}
