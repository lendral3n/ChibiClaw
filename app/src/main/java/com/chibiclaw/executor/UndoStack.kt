package com.chibiclaw.executor

import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 8.5 — Undo stack.
 *
 * Not every action is reversible (you can't un-send an SMS), but a
 * surprising number are:
 *
 *   • Alarm creation → delete the alarm row we just inserted.
 *   • Clipboard set → restore the previous clipboard content.
 *   • DeepSettings put → re-put the prior value we saved in the
 *     undo entry.
 *   • Volume / brightness change → set it back to the prior value.
 *   • App force-stop → launch the app again.
 *
 * The orchestrator pushes an [Entry] after each *successful*
 * irreversible-ish operation, and the user can say "undo" (or tap
 * the undo button in the UI) to pop the last entry and execute its
 * reverse function. Nothing here is persisted — if Chibi restarts,
 * the stack is empty.
 */
@Singleton
class UndoStack @Inject constructor() {

    data class Entry(
        val label: String,
        val timestamp: Long,
        val reverse: suspend () -> String
    )

    private val stack = ArrayDeque<Entry>()

    fun push(label: String, reverse: suspend () -> String) {
        synchronized(stack) {
            stack.push(Entry(label, System.currentTimeMillis(), reverse))
            while (stack.size > MAX_SIZE) stack.pollLast()
        }
    }

    suspend fun undoLast(): String {
        val entry = synchronized(stack) { stack.pollFirst() } ?: return "undo: empty"
        return try {
            "undo:${entry.label} → ${entry.reverse.invoke()}"
        } catch (e: Exception) {
            "undo_error: ${e.message}"
        }
    }

    fun peek(): Entry? = synchronized(stack) { stack.peekFirst() }
    fun size(): Int = synchronized(stack) { stack.size }
    fun clear() = synchronized(stack) { stack.clear() }

    /** Labels newest-first, for UI display. */
    fun labels(limit: Int = 5): List<String> = synchronized(stack) {
        stack.take(limit).map { it.label }
    }

    companion object {
        private const val MAX_SIZE = 20
    }
}
