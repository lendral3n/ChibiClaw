package com.chibiclaw.memory.miner

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chibiclaw.data.database.MemoryCategory
import com.chibiclaw.data.repository.TaskRepository
import com.chibiclaw.memory.MemoryStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.time.ZoneId

/**
 * PatternMinerWorker — periodic weekly aggregate task history → habit
 * candidate kalau ada hour-bucket dengan signal kuat (>= 4 task di jam yang
 * sama, span minimal 7 hari).
 *
 * Output: MemoryRecord HABIT dengan key "auto:hour_NN" dan confidence 0.5
 * (low — user perlu confirm via inspector untuk boost ke 0.9).
 *
 * Phase 7 minimal: aggregate-only. Phase 9 polish: LLM-driven inference
 * (call Gemma untuk natural-language habit name).
 */
@HiltWorker
class PatternMinerWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val taskRepository: TaskRepository,
    private val memoryStore: MemoryStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val tasks = taskRepository.recentSnapshot(limit = SCAN_LIMIT)
        if (tasks.size < MIN_TASKS) {
            Timber.i("PatternMiner skip: only ${tasks.size} task (<$MIN_TASKS minimum)")
            return Result.success()
        }

        val zone = ZoneId.systemDefault()
        val hourBuckets = IntArray(24)
        val firstSeenMsByHour = LongArray(24) { Long.MAX_VALUE }
        val lastSeenMsByHour = LongArray(24) { 0L }

        tasks.forEach { task ->
            val ts = task.startedAt?.toEpochMilliseconds() ?: task.createdAt.toEpochMilliseconds()
            val hour = java.time.Instant.ofEpochMilli(ts).atZone(zone).hour
            hourBuckets[hour]++
            if (ts < firstSeenMsByHour[hour]) firstSeenMsByHour[hour] = ts
            if (ts > lastSeenMsByHour[hour]) lastSeenMsByHour[hour] = ts
        }

        val now = System.currentTimeMillis()
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000L

        var inserted = 0
        for (hour in 0..23) {
            val count = hourBuckets[hour]
            if (count < MIN_COUNT_PER_HOUR) continue
            val spanMs = lastSeenMsByHour[hour] - firstSeenMsByHour[hour]
            if (spanMs < sevenDaysMs) continue

            val confidence = (count.toFloat() / SCAN_LIMIT * 4f).coerceIn(0.4f, 0.7f)
            val key = "auto:hour_$hour"
            val value = buildJsonObject {
                put("name", JsonPrimitive("Aktivitas jam $hour"))
                put("schedule", JsonPrimitive("hour=$hour"))
                put("frequency", JsonPrimitive("weekly+, $count obs"))
                put("context", JsonPrimitive("auto_miner aggregate"))
                put("first_observed_at", JsonPrimitive(firstSeenMsByHour[hour]))
                put("last_observed_at", JsonPrimitive(lastSeenMsByHour[hour]))
                put("source", JsonPrimitive("PatternMinerWorker"))
            }.toString()

            memoryStore.remember(
                category = MemoryCategory.HABIT,
                key = key,
                valueJson = value,
                confidence = confidence,
                ttlDays = 90,   // habit candidate auto-expire kalau tidak di-confirm
            )
            inserted++
        }

        Timber.i("PatternMiner done: scanned ${tasks.size} tasks → $inserted habit candidates")
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "pattern_miner"
        private const val SCAN_LIMIT = 200
        private const val MIN_TASKS = 20
        private const val MIN_COUNT_PER_HOUR = 4
    }
}
