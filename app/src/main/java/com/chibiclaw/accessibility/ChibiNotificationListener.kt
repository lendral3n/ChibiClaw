package com.chibiclaw.accessibility

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * ChibiNotificationListener — Phase 3 minimal.
 *
 * Tujuan Phase 3:
 *  - Expose API untuk `world_get_notifications` tool (return list active notifs)
 *  - Provide SharedFlow notif events untuk Phase 6 InitiativeEngine (standing
 *    instruction trigger NOTIFICATION_POSTED)
 *
 * Service di-bind oleh Android system saat user enable di Settings →
 * Notifications → Notification access.
 */
class ChibiNotificationListener : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        ref = WeakReference(this)
        Timber.i("ChibiNotificationListener.onCreate()")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        ref = WeakReference(this)
        Timber.i("Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        ref = WeakReference(null)
        Timber.i("Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        // Phase 6: emit ke EventBus untuk InitiativeEngine
        val summary = NotificationSummary(
            packageName = sbn.packageName ?: "?",
            title = sbn.notification?.extras?.getCharSequence("android.title")?.toString() ?: "",
            text = sbn.notification?.extras?.getCharSequence("android.text")?.toString() ?: "",
            postedAt = sbn.postTime,
        )
        eventFlow.tryEmit(summary)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Phase 6: similarly
    }

    /**
     * Snapshot all active notifications. Dipanggil dari world_get_notifications.
     */
    fun snapshot(): List<NotificationSummary> {
        return runCatching { activeNotifications.orEmpty() }
            .getOrDefault(emptyArray())
            .map { sbn ->
                NotificationSummary(
                    packageName = sbn.packageName ?: "?",
                    title = sbn.notification?.extras?.getCharSequence("android.title")?.toString() ?: "",
                    text = sbn.notification?.extras?.getCharSequence("android.text")?.toString() ?: "",
                    postedAt = sbn.postTime,
                )
            }
    }

    companion object {
        @Volatile private var ref: WeakReference<ChibiNotificationListener> = WeakReference(null)
        fun instance(): ChibiNotificationListener? = ref.get()
        fun isConnected(): Boolean = ref.get() != null

        private val eventFlow = MutableSharedFlow<NotificationSummary>(replay = 0, extraBufferCapacity = 64)
        val events: SharedFlow<NotificationSummary> = eventFlow.asSharedFlow()
    }
}

data class NotificationSummary(
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
)
