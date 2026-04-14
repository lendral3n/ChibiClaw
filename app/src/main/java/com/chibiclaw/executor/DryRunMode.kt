package com.chibiclaw.executor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 8.4 — Dry-run mode.
 *
 * When enabled, the executor short-circuits every side-effectful
 * action and returns a simulated result string describing what
 * *would* have happened. Useful when:
 *
 *   • Debugging a new skill on the user's device.
 *   • Running the first-time-setup walkthrough ("here's what Chibi
 *     would do — try her!") without actually pressing buttons.
 *   • Letting the user practise a command flow before committing.
 *
 * Only actions that modify state (UI taps, settings writes, sends,
 * shizuku shells) are intercepted; read-only actions (scan_ui, OCR,
 * contacts search) pass through so the agent still has grounded
 * observations.
 */
@Singleton
class DryRunMode @Inject constructor() {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun enable() { _enabled.value = true }
    fun disable() { _enabled.value = false }
    fun set(value: Boolean) { _enabled.value = value }
    fun isEnabled(): Boolean = _enabled.value

    /**
     * Convert a ChibiAction into a human-readable dry-run summary.
     * Keep these short and descriptive; they're injected into the
     * chat bubble so the user can read and learn what Chibi was
     * *about* to do.
     */
    fun simulate(action: Any): String = when (action) {
        is LaunchAppAction -> "[dry] akan buka app: ${action.appName}"
        is IntentAction -> "[dry] akan kirim intent ${action.action} " +
            (if (action.packageName.isNotBlank()) "ke ${action.packageName}" else "")
        is UiInteractAction -> "[dry] akan UI ${action.action} → ${action.target}"
        is SetAlarmAction -> "[dry] akan set alarm ${action.hour}:${action.minute}"
        is SystemControlAction -> "[dry] akan set system ${action.target}=${action.state}"
        is GestureAction -> "[dry] akan gesture ${action.kind} @${action.x1},${action.y1}"
        is MessagingAction -> "[dry] akan kirim ${action.kind} ke ${action.recipient}: ${action.body.take(40)}"
        is VisionAnalyzeAction -> "[dry] akan vision_analyze ${action.mode}: ${action.query}"
        is ShizukuAction -> "[dry] akan Shizuku ${action.kind}: ${action.payload.take(40)}"
        is ClipboardAction -> "[dry] akan clipboard ${action.op}"
        is CaptureAction -> "[dry] akan capture ${action.kind}"
        is MediaSessionAction -> "[dry] akan media ${action.command}"
        is UtilityAction -> "[dry] akan util ${action.kind}: ${action.input.take(40)}"
        is ReportAction -> action.message // reports pass through — they're harmless
        is AskUserAction -> action.question
        else -> "[dry] akan jalankan ${action::class.simpleName}"
    }
}
