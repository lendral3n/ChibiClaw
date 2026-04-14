package com.chibiclaw.executor.tier4

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.chibiclaw.service.NotificationListener
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 5.5 — Notification controller.
 *
 * Uses the already-bound [NotificationListener] (no extra permission
 * needed — the user granted Notification access during setup) to:
 *
 *   • list currently-visible notifications,
 *   • dismiss by key / package,
 *   • trigger an action button (e.g. "Reply", "Mark as read"),
 *   • send a quick reply via the notification's RemoteInput when the
 *     app exposes one (WhatsApp, Telegram, Gmail, Messages, etc.).
 *
 * All methods degrade gracefully if the listener hasn't connected yet
 * — they return a `notif_error:*` string that the orchestrator can
 * observe and retry after prompting the user to grant access.
 */
@Singleton
class NotificationController @Inject constructor() {

    data class Entry(
        val key: String,
        val packageName: String,
        val id: Int,
        val tag: String?,
        val title: String,
        val text: String,
        val postTime: Long,
        val hasReply: Boolean
    )

    // ---- read paths ----

    fun listActive(): List<Entry> {
        val listener = NotificationListener.getInstance() ?: return emptyList()
        val sbns: Array<StatusBarNotification> = try {
            listener.activeNotifications ?: emptyArray()
        } catch (_: Exception) {
            return emptyList()
        }
        return sbns.map { sbn -> sbn.toEntry() }.sortedByDescending { it.postTime }
    }

    fun listActiveFor(packageName: String): List<Entry> =
        listActive().filter { it.packageName == packageName }

    fun count(): Int = listActive().size

    fun format(limit: Int = 10): String {
        val list = listActive().take(limit)
        if (list.isEmpty()) return "[notifications] empty"
        val sb = StringBuilder("[notifications]\n")
        list.forEach { e ->
            sb.append("• ${e.packageName}: \"${e.title.take(40)}\" → ${e.text.take(80)}\n")
        }
        return sb.toString()
    }

    // ---- write paths ----

    fun cancel(key: String): String {
        val listener = NotificationListener.getInstance() ?: return "notif_error: listener_not_bound"
        return try {
            listener.cancelNotification(key)
            "notif_cancelled:$key"
        } catch (e: Exception) {
            "notif_error: ${e.message}"
        }
    }

    fun cancelAllFor(packageName: String): String {
        val listener = NotificationListener.getInstance() ?: return "notif_error: listener_not_bound"
        val matching = listActiveFor(packageName)
        if (matching.isEmpty()) return "notif_noop: no_notifications_for=$packageName"
        var dismissed = 0
        for (entry in matching) {
            try {
                listener.cancelNotification(entry.key)
                dismissed++
            } catch (_: Exception) { /* continue */ }
        }
        return "notif_cancelled:$packageName count=$dismissed"
    }

    fun cancelAll(): String {
        val listener = NotificationListener.getInstance() ?: return "notif_error: listener_not_bound"
        return try {
            listener.cancelAllNotifications()
            "notif_cancelled_all"
        } catch (e: Exception) {
            "notif_error: ${e.message}"
        }
    }

    /**
     * Quick-reply to a notification. The app must expose a
     * RemoteInput-backed action (WhatsApp, Telegram, Gmail, Messages,
     * Teams all do). Returns `notif_replied:$key` on success.
     */
    fun reply(key: String, message: String): String {
        val listener = NotificationListener.getInstance() ?: return "notif_error: listener_not_bound"
        val sbn = listener.activeNotifications?.firstOrNull { it.key == key }
            ?: return "notif_error: not_found=$key"
        val action = findReplyAction(sbn) ?: return "notif_error: no_reply_action=$key"
        return try {
            val remoteInput = action.remoteInputs?.firstOrNull()
                ?: return "notif_error: no_remote_input=$key"
            val intent = Intent()
            val bundle = Bundle().apply { putCharSequence(remoteInput.resultKey, message) }
            RemoteInput.addResultsToIntent(action.remoteInputs, intent, bundle)
            action.actionIntent.send(listener, 0, intent)
            "notif_replied:$key"
        } catch (e: Exception) {
            "notif_error: ${e.message}"
        }
    }

    fun triggerAction(key: String, actionTitleSubstring: String): String {
        val listener = NotificationListener.getInstance() ?: return "notif_error: listener_not_bound"
        val sbn = listener.activeNotifications?.firstOrNull { it.key == key }
            ?: return "notif_error: not_found=$key"
        val action = sbn.notification.actions?.firstOrNull { a ->
            a.title?.toString()?.contains(actionTitleSubstring, ignoreCase = true) == true
        } ?: return "notif_error: no_action=$actionTitleSubstring"
        return try {
            action.actionIntent.send()
            "notif_action_sent:$key → ${action.title}"
        } catch (e: Exception) {
            "notif_error: ${e.message}"
        }
    }

    // ---- helpers ----

    private fun findReplyAction(sbn: StatusBarNotification): Notification.Action? {
        val actions = sbn.notification.actions ?: return null
        return actions.firstOrNull { a ->
            !a.remoteInputs.isNullOrEmpty()
        }
    }

    private fun StatusBarNotification.toEntry(): Entry {
        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val hasReply = findReplyAction(this) != null
        return Entry(
            key = key,
            packageName = packageName,
            id = id,
            tag = tag,
            title = title,
            text = text,
            postTime = postTime,
            hasReply = hasReply
        )
    }
}
