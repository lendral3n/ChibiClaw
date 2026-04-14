package com.chibiclaw.memory.context

import com.chibiclaw.executor.tier2.ClipboardExecutor
import com.chibiclaw.perception.context.ScreenStateCache
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3.3 — Cross-app linker.
 *
 * Users constantly say things like *"buka link dari WA tadi"* or
 * *"kirim nomor yang barusan dari Telegram ke istriku"*. To translate
 * that into an executable plan Gemma needs to know which piece of text
 * came from which app. We don't have per-app clipboard history (Android
 * blocks that) so we approximate by joining:
 *
 *   • [ScreenStateCache] — the most-recent screen snapshots, which carry
 *     the source package name at the time a UI snapshot was taken.
 *   • [ClipboardExecutor.snapshotHistory] — every string the user or
 *     Chibi copied, newest first.
 *   • [RecentActivityFeed] — the most-recent *commands* and their
 *     results, tagged with tier and timestamp.
 *
 * The output is a short human-readable string we can inject into the
 * context block so Gemma can resolve "yang tadi dari WA" to an actual
 * URL or phone number. Nothing here is persisted — all sources are
 * already in-memory buffers and rebuilding this on each turn is fast.
 */
@Singleton
class CrossAppLinker @Inject constructor(
    private val screenStateCache: ScreenStateCache,
    private val clipboardExecutor: ClipboardExecutor,
    private val recentActivityFeed: RecentActivityFeed
) {

    data class Link(
        val value: String,
        val sourcePackage: String?,
        val kind: Kind,
        val timestamp: Long
    )

    enum class Kind { URL, PHONE, EMAIL, TEXT }

    /**
     * Scan the last few screen snapshots for copy-worthy tokens (URLs,
     * phones, emails), align them with clipboard history by equality,
     * and return a list newest-first. If [packageHint] is non-null we
     * keep only links whose source package matches.
     */
    fun links(packageHint: String? = null, limit: Int = 8): List<Link> {
        val snapshots = screenStateCache.recent(6)
        val clip = clipboardExecutor.snapshotHistory()

        val seen = linkedSetOf<String>()
        val out = mutableListOf<Link>()

        // 1. Snapshot-derived links carry a reliable source package.
        for (snap in snapshots) {
            val pkg = snap.packageName
            if (packageHint != null && pkg != packageHint) continue
            for (token in extractTokens(snap.uiMap)) {
                if (!seen.add(token)) continue
                out.add(Link(token, pkg, classify(token), snap.timestamp))
                if (out.size >= limit) return out
            }
        }

        // 2. Fall back to clipboard tokens whose package we can't be
        //    sure of — we tag them with the most-recent snapshot pkg.
        val fallbackPkg = snapshots.firstOrNull()?.packageName
        for (entry in clip) {
            val token = entry.trim()
            if (token.isEmpty()) continue
            if (!seen.add(token)) continue
            val kind = classify(token)
            if (kind == Kind.TEXT && token.length > 120) continue
            if (packageHint != null && fallbackPkg != packageHint) continue
            out.add(Link(token, fallbackPkg, kind, System.currentTimeMillis()))
            if (out.size >= limit) return out
        }

        return out
    }

    /**
     * Find the most-recent URL/phone/email that came from a specific
     * app (e.g. "link dari WA tadi"). Returns null if no match.
     */
    fun latestFrom(packageHint: String, kind: Kind? = null): Link? {
        return links(packageHint).firstOrNull { kind == null || it.kind == kind }
    }

    /** Pretty-print the top [limit] cross-app links for Gemma context. */
    fun format(limit: Int = 5): String {
        val recent = recentActivityFeed.entries.value.takeLast(3)
        val linkList = links(limit = limit)
        if (linkList.isEmpty() && recent.isEmpty()) return ""
        val sb = StringBuilder("[cross_app]\n")
        linkList.forEach { link ->
            sb.append("• ${link.kind.name.lowercase()} ← ${link.sourcePackage ?: "?"} : ${link.value.take(80)}\n")
        }
        return sb.toString()
    }

    // ---- helpers ----

    private fun extractTokens(uiMap: String): List<String> {
        if (uiMap.isBlank()) return emptyList()
        val out = mutableListOf<String>()
        URL_REGEX.findAll(uiMap).forEach { out.add(it.value.trimEnd('.', ',', ')', ']')) }
        EMAIL_REGEX.findAll(uiMap).forEach { out.add(it.value) }
        PHONE_REGEX.findAll(uiMap).forEach { out.add(it.value.trim()) }
        return out.distinct()
    }

    private fun classify(token: String): Kind = when {
        URL_REGEX.matches(token) -> Kind.URL
        EMAIL_REGEX.matches(token) -> Kind.EMAIL
        PHONE_REGEX.matches(token.replace("\\s".toRegex(), "")) -> Kind.PHONE
        else -> Kind.TEXT
    }

    companion object {
        private val URL_REGEX = Regex("""https?://[\w.\-/%?=&#+:;,@]+""")
        private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
        private val PHONE_REGEX = Regex("""(?<![A-Za-z0-9])(?:\+?\d[\d\s\-()]{6,}\d)""")
    }
}
