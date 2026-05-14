package com.chibiclaw.agent.initiative

import com.chibiclaw.agent.initiative.trigger.EventType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-process event bus untuk InitiativeEngine subscription.
 *
 * Producer: BroadcastReceivers, NotificationListenerService, GeofenceClient
 * callback, AlarmManager Intents, UsageStatsManager polling.
 *
 * Consumer: InitiativeEngine collect & match ke standing instructions.
 *
 * Replay 0 — bukan event sourcing, hanya signal. Hot flow buffer kecil
 * supaya kalau collector lambat tidak block emitter.
 */
@Singleton
class EventBus @Inject constructor() {

    private val _events = MutableSharedFlow<TriggerEvent>(
        replay = 0,
        extraBufferCapacity = 32,
    )

    val events: SharedFlow<TriggerEvent> = _events.asSharedFlow()

    /** Non-suspending emit (tryEmit) — kalau buffer full, event di-drop dan
     *  logged. Acceptable untuk InitiativeEngine semantics (signal not state). */
    fun emit(event: TriggerEvent): Boolean = _events.tryEmit(event)
}

/**
 * Event payload yang InitiativeEngine pakai untuk match trigger.
 *
 * `metadata` flat map untuk filter (package, title, text, value).
 */
data class TriggerEvent(
    val type: EventType,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
)
