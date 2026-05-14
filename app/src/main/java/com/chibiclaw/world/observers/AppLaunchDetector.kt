package com.chibiclaw.world.observers

import android.app.usage.UsageStatsManager
import android.content.Context
import com.chibiclaw.agent.initiative.EventBus
import com.chibiclaw.agent.initiative.TriggerEvent
import com.chibiclaw.agent.initiative.trigger.EventType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AppLaunchDetector — poll UsageStatsManager event stream (per 10s) untuk
 * detect APP_LAUNCHED (foreground app changed) + APP_BACKGROUNDED.
 *
 * Permission: PACKAGE_USAGE_STATS (special access via Settings).
 *
 * Phase 6 minimal — Phase 9 polish: subscribe via AccessibilityService
 * onWindowStateChanged untuk latency lebih cepat.
 */
@Singleton
class AppLaunchDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventBus: EventBus,
) {

    private val usm by lazy { context.getSystemService(UsageStatsManager::class.java) }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile private var pollJob: Job? = null
    @Volatile private var lastPolledMs: Long = System.currentTimeMillis()
    @Volatile private var currentForeground: String? = null

    fun start() {
        if (pollJob?.isActive == true) return
        Timber.i("AppLaunchDetector starting (poll 10s)")
        pollJob = scope.launch {
            while (true) {
                runCatching { pollOnce() }.onFailure { Timber.v(it, "AppLaunch poll skip") }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    private fun pollOnce() {
        val now = System.currentTimeMillis()
        val events = runCatching { usm.queryEvents(lastPolledMs, now) }.getOrNull() ?: return
        val ev = android.app.usage.UsageEvents.Event()
        var newForeground: String? = currentForeground
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            when (ev.eventType) {
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    newForeground = ev.packageName
                }
                android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (ev.packageName == currentForeground) {
                        eventBus.emit(
                            TriggerEvent(
                                type = EventType.APP_BACKGROUNDED,
                                metadata = mapOf("package" to ev.packageName.orEmpty()),
                            ),
                        )
                    }
                }
            }
        }
        if (newForeground != null && newForeground != currentForeground && newForeground != "com.chibiclaw") {
            eventBus.emit(
                TriggerEvent(
                    type = EventType.APP_LAUNCHED,
                    metadata = mapOf("package" to newForeground),
                ),
            )
            currentForeground = newForeground
        }
        lastPolledMs = now
    }

    companion object {
        private const val POLL_INTERVAL_MS = 10_000L
    }
}
