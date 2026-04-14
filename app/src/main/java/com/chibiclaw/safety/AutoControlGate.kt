package com.chibiclaw.safety

import com.chibiclaw.debug.DevLogger
import com.chibiclaw.executor.ChibiAction
import com.chibiclaw.executor.IntentAction
import com.chibiclaw.executor.LaunchAppAction
import com.chibiclaw.executor.UiInteractAction
import com.chibiclaw.service.ShizukuHandler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enforces the per-app [AutoControlConfig] policy just before
 * [com.chibiclaw.executor.ExecutionRouter] hands an action to its tier
 * executor. Called for every ChibiAction — it's a pure policy check, no
 * side effects, no I/O beyond looking up the config.
 *
 * The gate DECIDES, it does not EXECUTE — ExecutionRouter still owns the
 * dispatch to tier1..4. Separation of concerns keeps the tier executors
 * free of policy code so they can be unit-tested in isolation.
 *
 * Decision table (simplified):
 *
 *   1. Non-package action (SystemControl, SetAlarm, AskUser, Report, etc.)
 *      → ALWAYS ALLOW. These don't target a single app.
 *
 *   2. Package resolved + NO explicit config entry:
 *      → ALLOW via foreground. Brand-new installs should just work.
 *
 *   3. Package has a config entry:
 *      → Check [AutoControlConfig.allowedActions]. If the action kind is
 *        not in the whitelist → DENY with reason "action_not_allowed".
 *      → If the user asked for background mode but [ShizukuHandler] is
 *        unavailable → DENY with reason "shizuku_unavailable" so the UI
 *        can prompt to install Shizuku.
 *      → Otherwise ALLOW and let the router proceed.
 */
@Singleton
class AutoControlGate @Inject constructor(
    private val repository: AutoControlRepository,
    private val shizukuHandler: ShizukuHandler,
    private val devLogger: DevLogger
) {
    sealed class Decision {
        /** Proceed — the caller should execute the action as normal. */
        object Allow : Decision()

        /** Reject — [reason] is a machine tag, [message] is user-facing. */
        data class Deny(val reason: String, val message: String) : Decision()
    }

    fun check(action: ChibiAction): Decision {
        val pkg = packageFor(action) ?: return Decision.Allow
        val cfg = repository.get(pkg) ?: return Decision.Allow  // no explicit entry = allowed

        val kind = actionKind(action)
        if (!cfg.isActionAllowed(kind)) {
            devLogger.w("AUTO_CTRL", "DENY: $pkg action=$kind not in whitelist=${cfg.allowedActions}")
            return Decision.Deny(
                reason = "action_not_allowed",
                message = "Fuu tidak diizinkan melakukan $kind di $pkg. " +
                    "Ubah di Settings → Safety → Auto-Control."
            )
        }

        // Foreground disabled AND background disabled → total block
        if (!cfg.foregroundEnabled && !cfg.backgroundEnabled) {
            devLogger.w("AUTO_CTRL", "DENY: $pkg fully disabled (fg=false bg=false)")
            return Decision.Deny(
                reason = "package_disabled",
                message = "Fuu diblok untuk $pkg. Aktifkan di Settings → Safety → Auto-Control."
            )
        }

        // Background-only + Shizuku unavailable → block with actionable
        // message. Foreground fallback is caller-driven (ExecutionRouter
        // decides whether to downgrade silently or ask) — the gate just
        // reports the condition.
        if (cfg.backgroundEnabled && !cfg.foregroundEnabled && !shizukuHandler.isAvailable()) {
            devLogger.w("AUTO_CTRL", "DENY: $pkg background-only but Shizuku down")
            return Decision.Deny(
                reason = "shizuku_unavailable",
                message = "Mode background untuk $pkg butuh Shizuku aktif. " +
                    "Install/start Shizuku atau aktifkan mode foreground."
            )
        }

        devLogger.d("AUTO_CTRL", "ALLOW: $pkg kind=$kind fg=${cfg.foregroundEnabled} bg=${cfg.backgroundEnabled}")
        return Decision.Allow
    }

    /**
     * Derives a target package name from an action, or null if the action
     * isn't tied to a specific package. System toggles (flashlight, alarm,
     * wait, report) are package-agnostic and bypass the gate entirely.
     */
    private fun packageFor(action: ChibiAction): String? = when (action) {
        is IntentAction -> action.packageName.takeIf { it.isNotBlank() }
        is LaunchAppAction -> null  // resolved at runtime by AppLauncher via label
        is UiInteractAction -> null // targets current-foreground window, not a fixed pkg
        else -> null
    }

    /**
     * Maps [ChibiAction] to the coarse action kind used by
     * [AutoControlConfig.allowedActions].
     */
    private fun actionKind(action: ChibiAction): String = when (action) {
        is IntentAction -> "intent"
        is LaunchAppAction -> "launch"
        is UiInteractAction -> when (action.action.lowercase()) {
            "click", "tap" -> "tap"
            "type", "input" -> "type"
            "scroll", "swipe" -> "scroll"
            "back" -> "back"
            else -> action.action.lowercase()
        }
        else -> "other"
    }
}
