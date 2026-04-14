package com.chibiclaw.memory.context

import com.chibiclaw.memory.MemoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Phase 3.4 — User habit tracker.
 *
 * Builds a simple time-of-day × weekday histogram of which *skill*
 * the user invokes. The orchestrator can then whisper a suggestion
 * into Gemma's context ("user usually opens Spotify at 07:30 on
 * weekdays") without needing any ML — the histograms themselves are
 * the model.
 *
 * Everything is kept in-memory and is cheap enough to rebuild on
 * process start by replaying the last ~200 entries of
 * [MemoryManager.getRecentCommands]. Nothing in this class persists
 * anything new; the source of truth remains the command history
 * table that was already being written by the orchestrator.
 */
@Singleton
class UserHabitTracker @Inject constructor(
    private val memoryManager: MemoryManager
) {

    data class Slot(val dayOfWeek: Int, val hour: Int)
    data class Habit(val slot: Slot, val command: String, val count: Int)

    // Map<slot, Map<commandKey, count>>
    private val histogram = mutableMapOf<Slot, MutableMap<String, Int>>()
    private val _lastRebuilt = MutableStateFlow(0L)
    val lastRebuilt: StateFlow<Long> = _lastRebuilt

    /**
     * Rebuild the histogram from the last [window] commands in history.
     * Safe to call from a background coroutine; callers shouldn't await
     * this on the critical path — the orchestrator typically kicks it
     * off at boot and then once an hour.
     */
    suspend fun rebuild(window: Int = 200) {
        val commands = try {
            memoryManager.getRecentCommands(window)
        } catch (_: Exception) {
            return
        }
        val fresh = mutableMapOf<Slot, MutableMap<String, Int>>()
        for (c in commands) {
            if (c.state != "DONE") continue // only reinforce successful habits
            val slot = slotOf(c.timestamp)
            val key = commandKey(c.command)
            val bucket = fresh.getOrPut(slot) { mutableMapOf() }
            bucket[key] = (bucket[key] ?: 0) + 1
        }
        synchronized(histogram) {
            histogram.clear()
            histogram.putAll(fresh)
        }
        _lastRebuilt.value = System.currentTimeMillis()
    }

    /**
     * Record a single fresh execution — called by the orchestrator
     * after every successful command so the histogram stays warm
     * without requiring a full rebuild.
     */
    fun recordLive(command: String, timestamp: Long = System.currentTimeMillis()) {
        val slot = slotOf(timestamp)
        val key = commandKey(command)
        synchronized(histogram) {
            val bucket = histogram.getOrPut(slot) { mutableMapOf() }
            bucket[key] = (bucket[key] ?: 0) + 1
        }
    }

    /**
     * Top-N habits for *this exact* slot — use when building the
     * context block. Typical call: `top(slotOf(now()), n = 3)`.
     */
    fun top(slot: Slot = slotOf(System.currentTimeMillis()), n: Int = 3): List<Habit> {
        val bucket = synchronized(histogram) { histogram[slot]?.toMap() } ?: return emptyList()
        return bucket.entries
            .sortedByDescending { it.value }
            .take(n)
            .map { Habit(slot, it.key, it.value) }
    }

    /**
     * Top-N habits collapsed across the ±1 hour neighbourhood, which
     * smooths out the "07:29 vs 07:31" noise users don't care about.
     */
    fun topNeighbourhood(now: Long = System.currentTimeMillis(), n: Int = 3): List<Habit> {
        val center = slotOf(now)
        val slots = listOf(
            center,
            Slot(center.dayOfWeek, (center.hour + 23) % 24),
            Slot(center.dayOfWeek, (center.hour + 1) % 24)
        )
        val merged = mutableMapOf<String, Int>()
        synchronized(histogram) {
            slots.forEach { s ->
                histogram[s]?.forEach { (k, v) -> merged[k] = (merged[k] ?: 0) + v }
            }
        }
        return merged.entries
            .sortedByDescending { it.value }
            .take(n)
            .map { Habit(center, it.key, it.value) }
    }

    /** Pretty-print the top habits for injection into Gemma's context. */
    fun format(): String {
        val top = topNeighbourhood()
        if (top.isEmpty()) return ""
        val sb = StringBuilder("[user_habits]\n")
        top.forEach { h ->
            sb.append("• ${h.command} (seen ${h.count}×)\n")
        }
        return sb.toString()
    }

    // ---- helpers ----

    private fun slotOf(timestamp: Long): Slot {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        return Slot(cal.get(Calendar.DAY_OF_WEEK), cal.get(Calendar.HOUR_OF_DAY))
    }

    /**
     * Collapse a command string to a coarse key so "buka spotify",
     * "BUKA Spotify", and "buka spotify   " all increment the same
     * bucket. Stripped to the first 5 tokens to resist random tails.
     */
    private fun commandKey(raw: String): String {
        val trimmed = raw.trim().lowercase()
        val tokens = trimmed.split("\\s+".toRegex()).take(5)
        return tokens.joinToString(" ").take(max(1, 80))
    }
}
