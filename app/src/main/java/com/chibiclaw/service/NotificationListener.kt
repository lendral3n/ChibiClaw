package com.chibiclaw.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.chibiclaw.gateway.source.NotificationSource
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Notification listener service — the only OS hook that lets us see
 * notifications posted by *other* apps without MEDIA_CONTENT_CONTROL.
 *
 * Two responsibilities:
 *   1. Forward every new notification to [NotificationSource] so the
 *      gateway can decide whether to trigger a Chibi command.
 *   2. Expose the service *instance* as a singleton reference so
 *      [com.chibiclaw.executor.tier4.NotificationController] can
 *      call `cancelNotification()` / `getActiveNotifications()`
 *      without having to bind via AIDL.
 */
@AndroidEntryPoint
class NotificationListener : NotificationListenerService() {

    @Inject lateinit var notificationSource: NotificationSource

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d(TAG, "Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance === this) instance = null
        Log.d(TAG, "Listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        Log.d(TAG, "Notification from: ${sbn.packageName}")
        notificationSource.onNotificationPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    companion object {
        private const val TAG = "ChibiNotifListener"
        @Volatile private var instance: NotificationListener? = null
        fun getInstance(): NotificationListener? = instance
    }
}
