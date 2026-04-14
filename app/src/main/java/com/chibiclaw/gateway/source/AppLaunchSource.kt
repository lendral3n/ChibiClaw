package com.chibiclaw.gateway.source

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.chibiclaw.gateway.CommandGateway
import com.chibiclaw.gateway.CommandSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 7.3 — App-launch trigger source.
 *
 * Polls [UsageStatsManager] once per [POLL_MS] to catch the moment a
 * package is moved to the foreground. When a registered package
 * launches, the corresponding command is pushed to the gateway.
 *
 * Polling isn't ideal but Android doesn't offer a public callback
 * for "a specific third-party app just launched" — the only two
 * paths are this (needs PACKAGE_USAGE_STATS, which the user grants
 * during setup) or sniffing accessibility window events (which
 * would require all-windows access). 1.5 s polling at idle load is
 * about 0.2 % CPU on an Elite SoC — invisible on the flagship
 * battery budget.
 */
@Singleton
class AppLaunchSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val commandGateway: CommandGateway
) {

    private val triggers = ConcurrentHashMap<String, String>() // pkg → command
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null
    @Volatile private var lastEventTs: Long = System.currentTimeMillis()

    fun register(packageName: String, command: String) {
        triggers[packageName] = command
        ensureRunning()
    }

    fun unregister(packageName: String) {
        triggers.remove(packageName)
        if (triggers.isEmpty()) {
            pollJob?.cancel()
            pollJob = null
        }
    }

    fun unregisterAll() {
        triggers.clear()
        pollJob?.cancel()
        pollJob = null
    }

    private fun ensureRunning() {
        if (pollJob?.isActive == true) return
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return
        pollJob = scope.launch {
            while (isActive && triggers.isNotEmpty()) {
                try {
                    val now = System.currentTimeMillis()
                    val events = manager.queryEvents(lastEventTs, now)
                    val e = UsageEvents.Event()
                    while (events.hasNextEvent()) {
                        events.getNextEvent(e)
                        if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                            val pkg = e.packageName
                            val cmd = triggers[pkg]
                            if (cmd != null) {
                                Log.d(TAG, "App launched: $pkg → $cmd")
                                commandGateway.submitDirect(cmd, CommandSource.CRON)
                            }
                        }
                    }
                    lastEventTs = now
                } catch (se: SecurityException) {
                    Log.w(TAG, "PACKAGE_USAGE_STATS not granted — stopping poller")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Poll error: ${e.message}")
                }
                delay(POLL_MS)
            }
        }
    }

    companion object {
        private const val TAG = "AppLaunchSource"
        private const val POLL_MS = 1_500L
    }
}
