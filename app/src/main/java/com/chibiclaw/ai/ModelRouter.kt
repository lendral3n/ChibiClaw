package com.chibiclaw.ai

import com.chibiclaw.debug.DevLogger
import com.chibiclaw.memory.MemoryManager
import com.chibiclaw.memory.local.dao.AppPatternDao
import com.chibiclaw.memory.local.dao.CommandHistoryDao
import javax.inject.Inject
import javax.inject.Singleton

enum class ModelTier { E2B, E4B }

/**
 * Decides which Gemma tier ([ModelTier.E2B] vs [ModelTier.E4B]) should handle
 * a given command. Adaptive routing is back on — the previous "always E4B"
 * short-circuit is gone. Order of evaluation:
 *
 *   0. **Hard rules**: vision markers (screenshot / see-screen / image) always
 *      force E4B — the E2B model is text-only and cannot consume Bitmaps.
 *   1. **Complexity markers**: multi-step connectors ("lalu", "kemudian",
 *      "then", "and then", "sambil") force E4B — E2B reasoning chains are
 *      unreliable beyond a single tool call.
 *   2. **Package hint → AppPattern**: if the command mentions a concrete app
 *      ([packageHintFor]), look up its [AppPattern] row. If recent success rate
 *      for that package is below 80% at its last tier, ESCALATE one tier.
 *      Otherwise keep the learned avgTier.
 *   3. **Command-history match**: scan the last 20 completed commands for the
 *      same first word and reuse whichever tier most-recently succeeded for it.
 *   4. **Keyword heuristic**: simple-verb commands ("buka", "nyalakan", "alarm",
 *      "volume", "wifi", …) → E2B. Anything else falls through to E4B.
 *
 * route() is `suspend` because it hits Room DAOs — called from
 * [com.chibiclaw.core.ChibiOrchestrator.processCommand] which already owns a
 * coroutine, so the Room I/O never blocks the main thread.
 *
 * **Important:** The engine-manager [GemmaEngineManager.getEngine] falls back
 * E2B→E4B when the E2B slot is empty, so returning [ModelTier.E2B] is always
 * safe — if only E4B is actually loaded, the tier decision degenerates
 * gracefully to E4B at inference time. This means enabling adaptive routing
 * costs nothing when the user has a single-model setup.
 */
@Singleton
class ModelRouter @Inject constructor(
    private val appPatternDao: AppPatternDao,
    private val commandHistoryDao: CommandHistoryDao,
    private val devLogger: DevLogger
) {
    suspend fun route(command: String): ModelTier {
        val lower = command.lowercase()

        // 0. Vision → always E4B (E2B is text-only).
        if (VISION_MARKERS.any { lower.contains(it) }) {
            devLogger.d("ROUTING", "vision marker → E4B")
            return ModelTier.E4B
        }

        // 1. Multi-step complexity → always E4B.
        if (COMPLEXITY_MARKERS.any { lower.contains(it) }) {
            devLogger.d("ROUTING", "complexity marker → E4B")
            return ModelTier.E4B
        }

        // 2. Package hint → learned per-app avgTier with success-rate guard.
        val pkg = packageHintFor(lower)
        if (pkg != null) {
            val pattern = runCatching { appPatternDao.getByPackage(pkg) }.getOrNull()
            if (pattern != null && pattern.usageCount >= 3) {
                val learnedTier = when {
                    // Success rate dropped → escalate (E2B→E4B).
                    pattern.successRate < 0.80f && pattern.avgTier == 2 -> ModelTier.E4B
                    pattern.avgTier == 2 -> ModelTier.E2B
                    else -> ModelTier.E4B
                }
                devLogger.d(
                    "ROUTING",
                    "pattern[$pkg] tier=${pattern.avgTier} succ=${"%.2f".format(pattern.successRate)} → $learnedTier"
                )
                return learnedTier
            }
        }

        // 3. Command-history match on first word.
        tierFromHistory(lower)?.let { historyTier ->
            devLogger.d("ROUTING", "history match → $historyTier")
            return historyTier
        }

        // 4. Keyword heuristic — simple single-action commands default to E2B.
        val simple = SIMPLE_VERBS.any { lower.startsWith("$it ") || lower == it }
        val decision = if (simple) ModelTier.E2B else ModelTier.E4B
        devLogger.d("ROUTING", "fallback heuristic → $decision (simple=$simple)")
        return decision
    }

    /**
     * Looks through the last 20 command-history rows and returns the most
     * common execution tier among rows whose first word matches [lowered]'s
     * first word AND that finished with state=COMPLETED.
     *
     * This is a dumb-but-effective match: "alarm 7 pagi" and "alarm besok pagi"
     * both share first word "alarm" and will share routing decisions.
     */
    private suspend fun tierFromHistory(lowered: String): ModelTier? {
        val firstWord = lowered.substringBefore(' ').takeIf { it.isNotBlank() } ?: return null
        val recent = runCatching { commandHistoryDao.getRecent(20) }.getOrNull() ?: return null
        val matches = recent.filter {
            it.state == "COMPLETED" &&
                it.executionTier in listOf(2, 4) &&
                it.command.lowercase().startsWith(firstWord)
        }
        if (matches.isEmpty()) return null
        val e2bCount = matches.count { it.executionTier == 2 }
        val e4bCount = matches.count { it.executionTier == 4 }
        return when {
            e2bCount > e4bCount -> ModelTier.E2B
            e4bCount > e2bCount -> ModelTier.E4B
            else -> null
        }
    }

    /**
     * Best-effort mapping from a lowered command string to a concrete package
     * name so we can look up [AppPattern] rows. Returns null if nothing obvious
     * matches — the router will then fall back to history/heuristic.
     */
    private fun packageHintFor(lower: String): String? = when {
        lower.contains("whatsapp") || lower.contains(" wa ") || lower.startsWith("wa ") ->
            "com.whatsapp"
        lower.contains("telegram") || lower.contains(" tele ") ->
            "org.telegram.messenger"
        lower.contains("gmail") || lower.contains("email") ->
            "com.google.android.gm"
        lower.contains("kalkulator") || lower.contains("calculator") ->
            "com.google.android.calculator"
        lower.contains("kamera") || lower.contains("camera") ->
            "com.android.camera"
        lower.contains("youtube") || lower.contains(" yt ") ->
            "com.google.android.youtube"
        lower.contains("chrome") || lower.contains("browser") ->
            "com.android.chrome"
        lower.contains("alarm") || lower.contains("bangunkan") ->
            "com.google.android.deskclock"
        lower.contains("flashlight") || lower.contains("senter") ->
            "system.flashlight"
        lower.contains("volume") || lower.contains("suara") ->
            "system.volume"
        lower.contains("brightness") || lower.contains("cahaya layar") || lower.contains("kecerahan") ->
            "system.brightness"
        lower.contains("wifi") || lower.contains("wi-fi") ->
            "system.wifi"
        lower.contains("bluetooth") ->
            "system.bluetooth"
        else -> null
    }

    companion object {
        private val VISION_MARKERS = listOf(
            "screenshot", "lihat layar", "gambar", "foto layar", "what do you see",
            "apa yang ada di layar", "baca layar", "deskripsikan layar"
        )
        private val COMPLEXITY_MARKERS = listOf(
            " lalu ", " kemudian ", " setelah itu ", " then ", " after that ",
            " dan juga ", " sambil ", " sekaligus ", " multi", " beberapa"
        )
        // Single-word verbs that almost always resolve to a single tool call —
        // perfect E2B candidates. Anything with reasoning chains, composition,
        // or multi-app coordination falls through to E4B.
        private val SIMPLE_VERBS = listOf(
            "buka", "open", "launch", "jalankan", "mulai",
            "nyalakan", "matikan", "aktifkan", "padamkan", "hidupkan",
            "alarm", "bangunkan",
            "volume", "brightness", "cerahkan", "redupkan",
            "wifi", "wi-fi", "bluetooth",
            "senter", "flashlight",
            "telepon", "telpon", "call", "hubungi",
            "sms", "kirim"
        )
    }
}
