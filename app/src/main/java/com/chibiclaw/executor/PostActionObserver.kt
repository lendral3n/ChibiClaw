package com.chibiclaw.executor

import com.chibiclaw.perception.accessibility.SemanticDistiller
import com.chibiclaw.perception.accessibility.UiTreeScraper
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1.3 — auto-observation hook.
 *
 * After every UI-changing action we want Gemma to see the *new* state of
 * the screen in the same tool response, without forcing it to burn a
 * second tool call on `scan_ui`. [PostActionObserver.capture] is called
 * from [ExecutionRouter] right after UiInteractAction, GestureAction,
 * LaunchAppAction and any IntentAction that opens a foreground activity.
 *
 * The delay is short and intentional: the accessibility tree lags the
 * frame buffer by 1-2 frames on Android, and scrollable containers often
 * defer their layout pass until ~120 ms after the touch event lands. A
 * straight [getRootNode] call without the delay regularly returns the
 * PREVIOUS screen because the window content change event hasn't fired
 * yet. 250 ms is the sweet spot — big enough for RecyclerView / Compose
 * to settle, small enough that Gemma doesn't feel the lag.
 */
@Singleton
class PostActionObserver @Inject constructor(
    private val uiTreeScraper: UiTreeScraper,
    private val semanticDistiller: SemanticDistiller
) {

    /**
     * Returns a short observation string suitable for appending after a
     * primary action result. Format:
     *   `<primary result>\n[OBS]\n...distilled...\n[/OBS]`
     * The `[OBS]` wrapper lets the orchestrator strip it when routing to
     * chat-only presentation and makes it obvious where the action result
     * ends and the screen snapshot begins.
     */
    suspend fun capture(primaryResult: String, settleMs: Long = 250L): String {
        if (!uiTreeScraper.isAvailable()) return primaryResult
        delay(settleMs)
        val root = uiTreeScraper.getRootNode() ?: return primaryResult
        val map = try {
            semanticDistiller.distill(root)
        } catch (e: Exception) {
            return primaryResult
        }
        if (map.isBlank() || map == "[EMPTY UI]" || map == "[NO UI AVAILABLE]") return primaryResult
        return buildString {
            append(primaryResult)
            append("\n[OBS]\n")
            append(map.lineSequence().take(OBS_LINE_LIMIT).joinToString("\n"))
            append("\n[/OBS]")
        }
    }

    companion object {
        // Keep observation cheap — Gemma only needs the first ~30 interactive
        // rows to plan the next step; anything deeper usually isn't on the
        // current viewport anyway.
        private const val OBS_LINE_LIMIT = 30
    }
}
