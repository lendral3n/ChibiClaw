package com.chibiclaw.world.observers

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
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
 * CalendarEventObserver — poll CalendarContract.Instances setiap 60 detik,
 * emit `CALENDAR_EVENT_STARTING` 5 menit sebelum event.start_ms.
 *
 * Phase 9: AlarmManager precise broadcast lebih efisien tapi butuh
 * SCHEDULE_EXACT_ALARM permission yang user-toggled. Polling sederhana
 * dulu untuk MVP.
 */
@Singleton
class CalendarEventObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventBus: EventBus,
) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile private var pollJob: Job? = null
    private val emittedEventIds = mutableSetOf<Long>()

    fun start() {
        if (pollJob?.isActive == true) return
        Timber.i("CalendarEventObserver starting (poll 60s)")
        pollJob = scope.launch {
            while (true) {
                runCatching { pollOnce() }.onFailure { Timber.v(it, "Calendar poll skip") }
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
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val now = System.currentTimeMillis()
        val windowEnd = now + LOOKAHEAD_MS

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now)
        ContentUris.appendId(builder, windowEnd)
        val uri = builder.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.EVENT_LOCATION,
        )

        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(0)
                    val title = cursor.getString(1) ?: "(no title)"
                    val beginMs = cursor.getLong(2)
                    val location = cursor.getString(3).orEmpty()
                    val diff = beginMs - now
                    if (diff in 0..NOTIFY_BEFORE_MS && eventId !in emittedEventIds) {
                        eventBus.emit(
                            TriggerEvent(
                                type = EventType.CALENDAR_EVENT_STARTING,
                                metadata = mapOf(
                                    "event_id" to eventId.toString(),
                                    "title" to title,
                                    "begin_ms" to beginMs.toString(),
                                    "location" to location,
                                ),
                            ),
                        )
                        emittedEventIds += eventId
                    }
                }
            }

        // Prune emitted IDs untuk event yang sudah lewat 10 menit (avoid memory grow).
        emittedEventIds.removeAll { eventId -> false /* keep simple Phase 6 */ }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 60_000L
        private const val LOOKAHEAD_MS = 10L * 60 * 1000     // 10 menit ahead
        private const val NOTIFY_BEFORE_MS = 5L * 60 * 1000  // 5 menit sebelum start
    }
}
