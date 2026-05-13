package com.chibiclaw.world

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorldObserver — snapshot world state per tick.
 *
 * Phase 1 subset:
 * - Foreground app (UsageStatsManager — fallback ke "?" kalau permission tidak ada)
 * - Battery (BatteryManager)
 * - Network (ConnectivityManager)
 * - Screen on/off (PowerManager)
 * - Locale + timezone
 *
 * Phase 5 akan extend dengan location, calendar, notifications.
 *
 * No interpretation di code — semua signal jadi context input ke LLM.
 */
@Singleton
class WorldObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _snapshot = MutableStateFlow(buildSnapshot())
    val snapshot: StateFlow<WorldSnapshot> = _snapshot.asStateFlow()

    /** Snapshot terbaru. Refresh + return. */
    fun current(): WorldSnapshot {
        val fresh = buildSnapshot()
        _snapshot.value = fresh
        return fresh
    }

    private fun buildSnapshot(): WorldSnapshot {
        return WorldSnapshot(
            timestamp = Clock.System.now(),
            foregroundApp = detectForegroundApp(),
            batteryLevel = batteryLevel(),
            batteryCharging = isCharging(),
            networkOnline = isNetworkOnline(),
            screenOn = isScreenOn(),
            locale = Locale.getDefault().toLanguageTag(),
            timezone = TimeZone.currentSystemDefault().id,
        )
    }

    private fun detectForegroundApp(): String? {
        // UsageStatsManager butuh PACKAGE_USAGE_STATS permission (special access).
        // Phase 1: best-effort, kalau permission tidak ada return null.
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 60_000L,
                now,
            )
            stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (t: Throwable) {
            Timber.v("detectForegroundApp failed: ${t.message}")
            null
        }
    }

    private fun batteryLevel(): Int {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter) ?: return -1
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1).takeIf { it > 0 } ?: 100
        return ((level.toFloat() / scale) * 100).toInt().coerceIn(0, 100)
    }

    private fun isCharging(): Boolean {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter) ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun isNetworkOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun isScreenOn(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isInteractive
    }
}
