package com.chibiclaw.agent.initiative.trigger

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ComplexTrigger — sealed polymorphic.
 *
 * Sealed supaya bisa di-serialize/deserialize via kotlinx.serialization JSON
 * dengan discriminator `type`. Storage: JSON di `StandingInstructionEntity.triggerJson`.
 */
@Serializable
sealed class ComplexTrigger {

    /** Cron-based fire (mis. "0 7 * * MON-FRI"). */
    @Serializable
    @SerialName("time")
    data class Time(
        val cron: String,
        /** Optional jitter window detik supaya tidak fire persis di second 0. */
        val jitterSec: Int = 0,
    ) : ComplexTrigger()

    /** Event-driven (notif/battery/screen/network/app launch/calendar). */
    @Serializable
    @SerialName("event")
    data class Event(
        val eventType: EventType,
        val filter: TriggerFilter = TriggerFilter(),
    ) : ComplexTrigger()

    /** Predicate evaluated tiap tick (e.g. "battery.level < 30"). */
    @Serializable
    @SerialName("predicate")
    data class Predicate(
        val expression: String,
    ) : ComplexTrigger()

    /** Composite AND/OR/NOT. */
    @Serializable
    @SerialName("composite")
    data class Composite(
        val op: CompositeOp,
        val children: List<ComplexTrigger>,
    ) : ComplexTrigger()

    /** Geofence enter/exit ke saved place (memory key). */
    @Serializable
    @SerialName("geofence")
    data class Geofence(
        val placeMemoryKey: String,
        val enter: Boolean = true,
        val radiusMeters: Int = 200,
    ) : ComplexTrigger()
}

@Serializable
enum class CompositeOp { AND, OR, NOT }

@Serializable
enum class EventType {
    NOTIFICATION_POSTED,
    NOTIFICATION_REMOVED,
    BATTERY_LOW,
    BATTERY_CHARGING,
    BATTERY_UNPLUGGED,
    SCREEN_ON,
    SCREEN_OFF,
    USER_PRESENT,
    NETWORK_AVAILABLE,
    NETWORK_LOST,
    APP_LAUNCHED,
    APP_BACKGROUNDED,
    CALENDAR_EVENT_STARTING,
    GEOFENCE_ENTER,
    GEOFENCE_EXIT,
    BOOT_COMPLETED,
}

/**
 * Filter for events. Optional key/value/regex match.
 *
 * Examples:
 *  - {"package": "com.whatsapp"} — match notification dari WA
 *  - {"title_regex": ".*Mama.*"} — match title contains Mama
 *  - {"min": 30} (battery) — fire kalau level <= 30
 */
@Serializable
data class TriggerFilter(
    val packageName: String? = null,
    val titleRegex: String? = null,
    val textRegex: String? = null,
    val minValue: Int? = null,
    val maxValue: Int? = null,
    val extras: Map<String, String> = emptyMap(),
)
