package com.chibiclaw.world.observers

import com.chibiclaw.accessibility.ChibiNotificationListener
import com.chibiclaw.agent.initiative.EventBus
import com.chibiclaw.agent.initiative.TriggerEvent
import com.chibiclaw.agent.initiative.trigger.EventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge dari ChibiNotificationListener (system-instantiated, non-Hilt) ke
 * EventBus (Hilt-injected) supaya standing instruction NOTIFICATION_POSTED
 * trigger evaluasi langsung.
 *
 * Lifecycle: started oleh ChibiService.onCreate, stopped onDestroy.
 */
@Singleton
class NotificationEventBridge @Inject constructor(
    private val eventBus: EventBus,
) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile private var job: Job? = null

    fun start() {
        if (job != null) return
        Timber.i("NotificationEventBridge starting")
        job = scope.launch {
            ChibiNotificationListener.events.collect { summary ->
                eventBus.emit(
                    TriggerEvent(
                        type = EventType.NOTIFICATION_POSTED,
                        metadata = mapOf(
                            "package" to summary.packageName,
                            "title" to summary.title,
                            "text" to summary.text,
                            "posted_at" to summary.postedAt.toString(),
                        ),
                    ),
                )
            }
        }
    }

    fun stop() {
        Timber.i("NotificationEventBridge stopping")
        job?.cancel()
        job = null
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }
}
