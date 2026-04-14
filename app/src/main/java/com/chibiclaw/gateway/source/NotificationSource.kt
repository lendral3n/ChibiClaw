package com.chibiclaw.gateway.source

import android.service.notification.StatusBarNotification
import android.util.Log
import com.chibiclaw.gateway.CommandGateway
import com.chibiclaw.gateway.CommandSource
import com.chibiclaw.memory.pref.SecurePreferences
import com.chibiclaw.util.InstalledAppsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes system notifications and submits a command to [CommandGateway]
 * when a whitelisted app posts a notification whose text contains any of
 * the configured trigger keywords.
 *
 * The whitelist and keywords are read from [SecurePreferences] on every
 * call so the Notification Triggers settings screen can change them live
 * without needing a service restart.
 *
 * App labels are resolved via [InstalledAppsProvider] against the real
 * PackageManager — we no longer keep a hardcoded WA/Telegram/Gmail map,
 * so any package the user picks via the App Picker dialog gets a proper
 * label automatically.
 */
@Singleton
class NotificationSource @Inject constructor(
    private val commandGateway: CommandGateway,
    private val securePreferences: SecurePreferences,
    private val installedAppsProvider: InstalledAppsProvider
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        val whitelist = securePreferences.getNotificationWhitelist()
        if (pkg !in whitelist) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getString("android.title") ?: ""
        val text = extras.getString("android.text") ?: ""
        val combined = "$title $text".lowercase()

        val keywords = securePreferences.getNotificationKeywords()
        val matchedKeyword = keywords.firstOrNull { combined.contains(it.lowercase()) }
        if (matchedKeyword != null) {
            Log.d(TAG, "Notification trigger from $pkg: keyword='$matchedKeyword'")
            val command = buildCommand(pkg, title, text, matchedKeyword)
            scope.launch {
                commandGateway.submitDirect(command, CommandSource.NOTIFICATION)
            }
        }
    }

    private fun buildCommand(
        packageName: String,
        title: String,
        text: String,
        keyword: String
    ): String {
        val appName = installedAppsProvider.labelForPackage(packageName)
        return "Notifikasi dari $appName — $title: $text (keyword: $keyword)"
    }

    companion object {
        private const val TAG = "NotificationSource"
    }
}
