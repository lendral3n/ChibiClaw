package com.chibiclaw.executor.tier2

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.chibiclaw.executor.ClipboardAction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 5.3 — clipboard manager with a small in-memory history buffer.
 *
 * Android 13+ only lets the system clipboard be *read* by the foreground
 * app, so we keep our own rolling buffer (size 20) of every Set call so
 * Gemma can say "paste the 2nd previous link" without racing the OS.
 *
 * Note: we do NOT shadow-read the system clipboard here because doing so
 * from a non-foreground service just returns an empty ClipData on Android
 * 10+; the read path therefore falls back to the MOST RECENT set-value in
 * our own history.
 */
@Singleton
class ClipboardExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val history = ArrayDeque<String>()
    private val clipboard: ClipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    suspend fun perform(action: ClipboardAction): String {
        return when (action.op.lowercase()) {
            "get", "read" -> read()
            "set", "write", "copy" -> set(action.text)
            "history" -> dumpHistory()
            "clear" -> {
                history.clear()
                try {
                    clipboard.setPrimaryClip(ClipData.newPlainText("chibiclaw", ""))
                } catch (_: Exception) {}
                "clipboard_cleared"
            }
            else -> "clipboard_error: unknown op=${action.op}"
        }
    }

    private fun set(text: String): String {
        if (text.isEmpty()) return "clipboard_noop: empty"
        return try {
            val clip = ClipData.newPlainText("chibiclaw", text)
            clipboard.setPrimaryClip(clip)
            pushHistory(text)
            "clipboard_set: ${text.take(40)}"
        } catch (e: Exception) {
            pushHistory(text)
            "clipboard_set_history_only: ${e.message}"
        }
    }

    private fun read(): String {
        return try {
            val primary = clipboard.primaryClip
            val value = primary?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            if (value.isNotEmpty()) value
            else history.peekFirst() ?: "clipboard_empty"
        } catch (e: Exception) {
            history.peekFirst() ?: "clipboard_error: ${e.message}"
        }
    }

    private fun dumpHistory(): String {
        if (history.isEmpty()) return "clipboard_history: empty"
        return history.withIndex().joinToString("\n") { (i, v) ->
            "$i: ${v.take(80)}"
        }
    }

    /**
     * Expose an immutable snapshot of the history for other components
     * (e.g. CrossAppLinker) — they mustn't modify it.
     */
    fun snapshotHistory(): List<String> = synchronized(history) { history.toList() }

    private fun pushHistory(text: String) {
        history.remove(text) // dedupe
        history.addFirst(text)
        while (history.size > HISTORY_LIMIT) history.removeLast()
    }

    companion object {
        private const val HISTORY_LIMIT = 20
    }
}
