package com.chibiclaw.agent.tools.safety

import com.chibiclaw.agent.tools.ToolCall
import com.chibiclaw.agent.tools.ToolSpec
import com.chibiclaw.data.database.TaskEntity
import com.chibiclaw.service.overlay.OverlayWindowManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SafetyGate — Phase 3 inline confirmation untuk HIGH severity tool.
 *
 * Aturan:
 *  - CHAT channel: SELALU minta confirmation (user interactive).
 *  - STANDING channel: pre-authorize via StandingInstruction.preAuthorizedTools
 *    (Phase 6) — kalau tool listed di whitelist, skip overlay.
 *  - AUTONOMOUS channel: similar STANDING.
 *
 * Phase 3: CHAT channel pre-auth not implemented; STANDING pre-auth juga
 * defer Phase 6 (saat StandingInstruction entity live). Sekarang: SELALU
 * minta confirmation untuk HIGH severity. ToolDispatcher pakai dengan
 * 30 detik auto-deny default.
 */
@Singleton
class SafetyGate @Inject constructor(
    private val overlayWindowManager: OverlayWindowManager,
) {

    /**
     * Tampilkan confirmation modal, return true kalau user approve.
     *
     * Timeout default 30 detik → auto-deny.
     */
    suspend fun requestApproval(
        toolSpec: ToolSpec,
        call: ToolCall,
        task: TaskEntity,
        timeoutMs: Long = 30_000,
    ): Boolean {
        if (!shouldGate(toolSpec, task)) return true

        val deferred = CompletableDeferred<Boolean>()
        // WindowManager.addView WAJIB di Main thread.
        withContext(Dispatchers.Main) {
            overlayWindowManager.showConfirmation(
                toolSpec = toolSpec,
                call = call,
                timeoutMs = timeoutMs,
            ) { approved ->
                deferred.complete(approved)
            }
        }

        return try {
            withTimeout(timeoutMs + 1000L) { deferred.await() }
        } catch (t: TimeoutCancellationException) {
            Timber.w("SafetyGate auto-deny: timeout di outer scope")
            false
        }
    }

    /**
     * Decide apakah tool ini butuh gate. Phase 3: HIGH severity selalu gate
     * kecuali (Phase 6 nanti) preAuthorize via standing instruction.
     */
    private fun shouldGate(toolSpec: ToolSpec, task: TaskEntity): Boolean {
        if (toolSpec.safety.severity != com.chibiclaw.agent.tools.ToolSeverity.HIGH) return false
        // Phase 6: cek task.triggerSource → StandingInstruction.preAuthorizedTools
        // Phase 3: tidak ada whitelist, semua HIGH severity → gate
        @Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
        val channelHint = task.channel
        return true
    }
}
