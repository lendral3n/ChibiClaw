package com.chibiclaw.perception.context

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.SystemClock
import com.chibiclaw.service.ChibiAccessibility
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2.4 — detects which app the user is currently looking at.
 *
 * Android has no stable public API for "current foreground package" so
 * we use three fallback sources in priority order:
 *   1. [ChibiAccessibility.getInstance]?.rootInActiveWindow?.packageName
 *      — instant, but empty if the a11y service hasn't received a window
 *      content event yet (happens right after app launch).
 *   2. UsageStatsManager.queryEvents over the last 2 seconds, looking for
 *      the most recent MOVE_TO_FOREGROUND. Needs PACKAGE_USAGE_STATS grant.
 *   3. The last-known package from a tiny in-memory cache, updated by the
 *      accessibility service via [recordPackage]. Used as a final "best
 *      guess" so Gemma always has SOMETHING to work with.
 *
 * The detector is stateless on its own; persistence lives in
 * [com.chibiclaw.perception.context.ScreenStateCache].
 */
@Singleton
class ActiveAppDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var lastKnownPackage: String? = null

    fun recordPackage(pkg: String?) {
        if (pkg != null && pkg.isNotBlank()) lastKnownPackage = pkg
    }

    fun getCurrentPackage(): String? {
        ChibiAccessibility.getInstance()?.rootInActiveWindow?.packageName?.toString()
            ?.takeIf { it.isNotEmpty() && it != "com.android.systemui" }
            ?.let { recordPackage(it); return it }

        usageStatsPackage()?.let { recordPackage(it); return it }

        return lastKnownPackage
    }

    private fun usageStatsPackage(): String? {
        return try {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE)
                as? UsageStatsManager ?: return null
            val now = System.currentTimeMillis()
            val events = manager.queryEvents(now - 2_000L, now)
            val e = android.app.usage.UsageEvents.Event()
            var latestPkg: String? = null
            var latestTs = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND &&
                    e.timeStamp >= latestTs
                ) {
                    latestTs = e.timeStamp
                    latestPkg = e.packageName
                }
            }
            latestPkg
        } catch (_: SecurityException) {
            null // PACKAGE_USAGE_STATS not granted — ignore and fall through
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Human-friendly name of the current app, for use in chat bubbles.
     * Falls back to the raw package if the PackageManager doesn't resolve
     * a label (custom ROM / disabled app).
     */
    fun getCurrentAppLabel(): String {
        val pkg = getCurrentPackage() ?: return "unknown"
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            pkg
        }
    }

    /**
     * Monotonic timestamp useful for "app has been in foreground for N ms"
     * checks — the boot-based clock never goes backwards on daylight-savings.
     */
    @Suppress("unused")
    fun now(): Long = SystemClock.elapsedRealtime()
}
