package com.chibiclaw.executor

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-action timeout wrapper. The original single-timeout version capped
 * every action at 5 seconds, which is fine for atomic "send intent" style
 * work but far too tight for UI actions that need time for the screen to
 * settle, for the keyboard to open, or for a scroll animation to land.
 *
 * Split into three buckets:
 *  - FAST:   5 s  — intent_send, system_control, set_alarm, launch_app,
 *                   content_query, report. Things that either succeed
 *                   instantly or fail immediately.
 *  - UI:    15 s  — ui_interact, gesture, scan_ui. They have to wait for
 *                   the accessibility tree to refresh AND for any follow-up
 *                   observation/auto-scan.
 *  - VISION:25 s  — vision_analyze, multimodal. Runs one Gemma inference
 *                   on a full screenshot; even on 8 Elite this can take
 *                   10-20 s for the first prompt.
 */
@Singleton
class TimeoutGuard @Inject constructor() {

    companion object {
        const val FAST_ACTION_TIMEOUT_MS = 5_000L
        const val UI_ACTION_TIMEOUT_MS = 15_000L
        const val VISION_ACTION_TIMEOUT_MS = 25_000L
    }

    suspend fun <T> withActionTimeout(block: suspend () -> T): Result<T> =
        runWithTimeout(FAST_ACTION_TIMEOUT_MS, "fast", block)

    suspend fun <T> withUiTimeout(block: suspend () -> T): Result<T> =
        runWithTimeout(UI_ACTION_TIMEOUT_MS, "ui", block)

    suspend fun <T> withVisionTimeout(block: suspend () -> T): Result<T> =
        runWithTimeout(VISION_ACTION_TIMEOUT_MS, "vision", block)

    private suspend fun <T> runWithTimeout(
        timeoutMs: Long,
        label: String,
        block: suspend () -> T
    ): Result<T> = try {
        val result = withTimeout(timeoutMs) { block() }
        Result.success(result)
    } catch (e: TimeoutCancellationException) {
        Result.failure(Exception("$label action timed out after ${timeoutMs}ms"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
