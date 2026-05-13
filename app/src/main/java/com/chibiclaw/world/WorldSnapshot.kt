package com.chibiclaw.world

import kotlinx.datetime.Instant

/**
 * Snapshot world state — di-build per tick oleh WorldObserver.
 *
 * Phase 1: subset minimal (foreground app, battery, network, time).
 * Phase 5 akan tambah location, calendar, notif, dll.
 */
data class WorldSnapshot(
    val timestamp: Instant,
    val foregroundApp: String?,
    val batteryLevel: Int,             // 0-100
    val batteryCharging: Boolean,
    val networkOnline: Boolean,
    val screenOn: Boolean,
    val locale: String,
    val timezone: String,
) {
    fun toPromptText(): String = buildString {
        append("- timestamp: $timestamp\n")
        append("- foreground_app: ${foregroundApp ?: "unknown"}\n")
        append("- battery: $batteryLevel% ${if (batteryCharging) "(charging)" else "(unplugged)"}\n")
        append("- network: ${if (networkOnline) "online" else "offline"}\n")
        append("- screen: ${if (screenOn) "on" else "off"}\n")
        append("- locale: $locale, tz: $timezone")
    }
}
