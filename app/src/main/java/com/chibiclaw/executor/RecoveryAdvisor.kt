package com.chibiclaw.executor

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 8.1 — RecoveryAdvisor.
 *
 * When a step fails, the orchestrator would normally give up and
 * bubble the error. The RecoveryAdvisor provides a short suggestion
 * string ("try scrolling down and retrying the tap", "ask the user
 * to enable notifications permission") that gets injected into the
 * next inference turn so Gemma can self-correct.
 *
 * The advisor is intentionally rule-based — the point is to be
 * *fast* and *deterministic*. For actual multi-step replanning we
 * still rely on Gemma's chain-of-thought — we just seed the chain
 * with a nudge in the right direction.
 */
@Singleton
class RecoveryAdvisor @Inject constructor() {

    data class Advice(
        val reason: String,
        val suggestion: String,
        val retryable: Boolean
    )

    fun advise(errorText: String, lastAction: String? = null): Advice {
        val err = errorText.lowercase()
        return when {
            // ---- target not found ----
            "node_not_found" in err || "no_matching_node" in err -> Advice(
                reason = "Target tidak ditemukan di layar saat ini",
                suggestion = "Coba scroll ke bawah lalu ulangi, atau pakai vision_analyze(find_element) untuk cari koordinat.",
                retryable = true
            )
            // ---- timeout / slow app ----
            "timeout" in err -> Advice(
                reason = "Aksi melewati batas waktu",
                suggestion = "Tunggu sebentar dan scan_ui lagi — mungkin animasi atau jaringan lambat.",
                retryable = true
            )
            // ---- permission issue ----
            "denied" in err || "permission" in err || "write_secure" in err -> Advice(
                reason = "Izin tidak cukup",
                suggestion = "Minta user aktifkan izin terkait di Settings, atau gunakan Shizuku jika tersedia.",
                retryable = false
            )
            // ---- MediaProjection ----
            "user_denied_screen_capture" in err -> Advice(
                reason = "MediaProjection tidak diberikan",
                suggestion = "Minta user tap 'Start now' ketika dialog screen-capture muncul.",
                retryable = false
            )
            // ---- no such activity / app ----
            "not_found" in err || "no_launcher" in err -> Advice(
                reason = "App atau activity tidak terinstal",
                suggestion = "Cari app alternatif dengan nama serupa, atau tanyakan user.",
                retryable = false
            )
            // ---- Shizuku not available ----
            "shizuku_not_available" in err -> Advice(
                reason = "Shizuku belum aktif",
                suggestion = "Fallback ke flow via Settings app pakai intent, atau minta user start Shizuku service.",
                retryable = false
            )
            // ---- generic intent error ----
            "intent_rejected" in err || "intent_error" in err -> Advice(
                reason = "Intent ditolak oleh sistem",
                suggestion = "Coba action yang setara (misal buka app langsung) lalu lanjutkan via accessibility.",
                retryable = true
            )
            else -> Advice(
                reason = "Kegagalan tidak terklasifikasi",
                suggestion = "Replan dengan pendekatan berbeda (vision vs a11y).",
                retryable = true
            )
        }
    }

    /** Format a single line hint for injection into the next Gemma turn. */
    fun formatHint(advice: Advice, lastAction: String? = null): String {
        val head = if (lastAction != null) "[recovery_hint from=$lastAction]" else "[recovery_hint]"
        return "$head ${advice.reason} → ${advice.suggestion}"
    }
}
