package com.chibiclaw.memory.context

import com.chibiclaw.memory.MemoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3.2 — tiny in-memory ring buffer of the most recent user actions
 * and their outcomes. Used by the context assembler so Gemma can see
 * "what has happened in the last few minutes" without having to hit the
 * Room DB (which is fine but slower on cold reads).
 *
 * Entries are also persisted via [MemoryManager.saveCommand], so restart
 * won't lose them forever — the feed is just a low-latency cache.
 */
@Singleton
class RecentActivityFeed @Inject constructor(
    private val memoryManager: MemoryManager
) {

    data class Entry(
        val command: String,
        val result: String,
        val timestamp: Long,
        val tier: Int
    )

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    suspend fun record(command: String, result: String, tier: Int, state: String, severity: String) {
        val entry = Entry(command, result, System.currentTimeMillis(), tier)
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
        try {
            memoryManager.saveCommand(command, result, state, severity, tier)
        } catch (_: Exception) { /* best-effort — feed still works if DB is gone */ }
    }

    /** Pretty-print the feed for injection into Gemma's context. */
    fun format(): String {
        val list = _entries.value
        if (list.isEmpty()) return ""
        val sb = StringBuilder("[recent_activity]\n")
        list.takeLast(6).forEach { e ->
            sb.append("• \"${e.command.take(60)}\" → ${e.result.take(60)}\n")
        }
        return sb.toString()
    }

    companion object {
        private const val MAX_ENTRIES = 20
    }
}
