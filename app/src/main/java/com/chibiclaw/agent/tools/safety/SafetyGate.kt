package com.chibiclaw.agent.tools.safety

import com.chibiclaw.agent.tools.ToolCall
import com.chibiclaw.agent.tools.ToolSpec
import com.chibiclaw.agent.tools.ToolSeverity
import com.chibiclaw.data.database.TaskChannel
import com.chibiclaw.data.database.TaskEntity
import com.chibiclaw.data.repository.StandingInstructionRepository
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
 * SafetyGate — inline confirmation untuk HIGH severity tool.
 *
 * Aturan:
 *  - CHAT channel: SELALU minta confirmation (user interactive).
 *  - STANDING channel + task.triggerSource = "standing:<id>":
 *    lookup StandingInstruction.preAuthorizedTools — kalau tool listed di
 *    whitelist, skip overlay. Audit log tetap mencatat eksekusi.
 *  - AUTONOMOUS channel: defer Phase 7+ (sementara perlakukan sama dengan
 *    STANDING — kalau ada triggerSource cek pre-auth).
 */
@Singleton
class SafetyGate @Inject constructor(
    private val overlayWindowManager: OverlayWindowManager,
    private val standingRepo: StandingInstructionRepository,
) {

    /**
     * Tampilkan confirmation modal, return true kalau user approve atau
     * task channel == STANDING dengan tool pre-authorized.
     *
     * Timeout default 30 detik → auto-deny.
     */
    suspend fun requestApproval(
        toolSpec: ToolSpec,
        call: ToolCall,
        task: TaskEntity,
        timeoutMs: Long = 30_000,
    ): Boolean {
        if (toolSpec.safety.severity != ToolSeverity.HIGH) return true

        // Pre-auth path: STANDING/AUTONOMOUS channel + valid trigger source.
        if (task.channel != TaskChannel.CHAT && isPreAuthorized(toolSpec, task)) {
            Timber.i("Pre-authorized: tool=${toolSpec.name} via ${task.triggerSource}")
            return true
        }

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
     * Cek apakah task dari StandingInstruction yang sudah pre-authorize tool ini.
     * Format triggerSource: "standing:<id>".
     */
    private suspend fun isPreAuthorized(toolSpec: ToolSpec, task: TaskEntity): Boolean {
        if (!toolSpec.safety.preAuthorizable) return false
        val src = task.triggerSource ?: return false
        if (!src.startsWith("standing:")) return false
        val id = src.removePrefix("standing:")
        val entity = standingRepo.get(id) ?: return false
        return toolSpec.name in entity.preAuthorizedTools()
    }
}
