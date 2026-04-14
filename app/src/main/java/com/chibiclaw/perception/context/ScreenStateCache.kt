package com.chibiclaw.perception.context

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2.5 — rolling buffer of the last N screen snapshots.
 *
 * The accessibility executor and the vision executor both push a
 * [Snapshot] every time the screen changes; the agentic loop (Phase 8.1)
 * reads the most recent snapshot to decide whether a retry is needed
 * without re-scanning from scratch. Anything older than [MAX_ENTRIES] is
 * evicted to keep memory flat.
 *
 * The cache is process-local (not persisted) — screen states are cheap
 * to recreate but extremely expensive to serialise.
 */
@Singleton
class ScreenStateCache @Inject constructor() {

    data class Snapshot(
        val timestamp: Long,
        val packageName: String?,
        val activity: String?,
        val uiMap: String,
        val signature: Int
    )

    private val entries = ConcurrentLinkedDeque<Snapshot>()
    private val _current = MutableStateFlow<Snapshot?>(null)
    val current: StateFlow<Snapshot?> = _current

    fun push(snapshot: Snapshot) {
        // Collapse consecutive duplicates — there's no point storing the
        // same tree twice if nothing changed on screen.
        val last = entries.peekFirst()
        if (last != null && last.signature == snapshot.signature) {
            return
        }
        entries.addFirst(snapshot)
        while (entries.size > MAX_ENTRIES) entries.pollLast()
        _current.value = snapshot
    }

    fun latest(): Snapshot? = entries.peekFirst()

    /** Returns the N most recent snapshots, newest first. */
    fun recent(n: Int): List<Snapshot> = entries.take(n)

    fun clear() {
        entries.clear()
        _current.value = null
    }

    companion object {
        private const val MAX_ENTRIES = 12
    }
}
