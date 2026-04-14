package com.chibiclaw.executor.tier3

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smart resolver that finds the best [AccessibilityNodeInfo] for a given
 * human-friendly [query]. The resolver walks the full accessibility tree
 * and scores every visible, enabled node across multiple signals:
 * text / contentDescription / resource-id / hintText / className. The
 * highest-scoring node wins, with bounds-area used as tie-breaker so an
 * icon-sized Button wins over a full-screen Container that happens to
 * contain the same text.
 *
 * This replaces the naive "recursively search for any node whose text
 * contains `target`" in [AccessibilityExecutor], which frequently returned
 * the root layout instead of a clickable child. The resolver is reused by
 * [ScrollFinder] and [ChibiClawTools.uiInteract].
 */
@Singleton
class TargetResolver @Inject constructor() {

    data class TargetMatch(
        val node: AccessibilityNodeInfo,
        val score: Int,
        val matchedOn: String,
        val bounds: Rect
    )

    /**
     * Finds the single best match for [query]. Returns null when no node
     * scores above zero OR when the best match has bounds outside of the
     * visible screen (off-screen or zero area).
     */
    fun resolve(root: AccessibilityNodeInfo?, query: String): TargetMatch? {
        if (root == null || query.isBlank()) return null
        val candidates = mutableListOf<TargetMatch>()
        collect(root, query.trim().lowercase(), candidates)
        if (candidates.isEmpty()) return null
        // Sort by score descending, then by area ascending (smaller = more
        // likely to be the specific tappable element, not a container).
        return candidates.sortedWith(
            compareByDescending<TargetMatch> { it.score }
                .thenBy { it.bounds.width() * it.bounds.height() }
        ).first()
    }

    /**
     * Returns every match ordered by score, useful when the caller wants
     * to iterate candidates (scrollUntilFound, etc.).
     */
    fun resolveAll(root: AccessibilityNodeInfo?, query: String): List<TargetMatch> {
        if (root == null || query.isBlank()) return emptyList()
        val candidates = mutableListOf<TargetMatch>()
        collect(root, query.trim().lowercase(), candidates)
        return candidates.sortedWith(
            compareByDescending<TargetMatch> { it.score }
                .thenBy { it.bounds.width() * it.bounds.height() }
        )
    }

    private fun collect(
        node: AccessibilityNodeInfo,
        queryLower: String,
        out: MutableList<TargetMatch>,
        depth: Int = 0
    ) {
        if (depth > MAX_DEPTH) return
        if (!node.isVisibleToUser) {
            // Still descend — a hidden container can still hold visible children.
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { collect(it, queryLower, out, depth + 1) }
            }
            return
        }

        val bounds = Rect().also { node.getBoundsInScreen(it) }
        if (!bounds.isEmpty) {
            val score = scoreNode(node, queryLower)
            if (score > 0) {
                val matchedOn = whichFieldMatched(node, queryLower)
                out.add(TargetMatch(node, score, matchedOn, bounds))
            }
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collect(it, queryLower, out, depth + 1) }
        }
    }

    /**
     * Scoring ladder — higher is better. The absolute numbers are chosen
     * so that a lower tier NEVER beats a higher tier no matter how long
     * the string is (each tier is separated by > MAX_BONUS).
     *
     *  2000  exact resource-id suffix match
     *  1800  exact text match
     *  1600  exact contentDescription match
     *  1400  exact hint match
     *  1200  token-boundary text match (word in query ≡ word in label)
     *  1000  substring text match
     *   800  substring contentDesc match
     *   600  substring resource-id match
     *   400  substring hint match
     *   200  className match (e.g. "ImageButton" for query "tombol")
     *     0  no match
     *
     * Bonuses added on top (capped at +150 total):
     *  +50   node is clickable
     *  +30   node is an input / editable
     *  +20   node is enabled (always true for most matches, small bump)
     *  +10 * token overlap for query with >= 2 tokens
     */
    private fun scoreNode(node: AccessibilityNodeInfo, queryLower: String): Int {
        val text = node.text?.toString()?.lowercase().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
        val resId = node.viewIdResourceName?.lowercase().orEmpty()
        val resIdSuffix = resId.substringAfterLast("/")
        val hint = node.hintText?.toString()?.lowercase().orEmpty()
        val cls = node.className?.toString()?.lowercase().orEmpty()

        val base = when {
            resIdSuffix.isNotEmpty() && resIdSuffix == queryLower -> 2000
            text.isNotEmpty() && text == queryLower -> 1800
            desc.isNotEmpty() && desc == queryLower -> 1600
            hint.isNotEmpty() && hint == queryLower -> 1400
            text.isNotEmpty() && tokenEquals(text, queryLower) -> 1200
            text.isNotEmpty() && text.contains(queryLower) -> 1000
            desc.isNotEmpty() && desc.contains(queryLower) -> 800
            resId.isNotEmpty() && resId.contains(queryLower) -> 600
            hint.isNotEmpty() && hint.contains(queryLower) -> 400
            cls.isNotEmpty() && classHintMatches(cls, queryLower) -> 200
            else -> 0
        }
        if (base == 0) return 0

        var bonus = 0
        if (node.isClickable) bonus += 50
        if (node.isEditable) bonus += 30
        if (node.isEnabled) bonus += 20
        if (queryLower.contains(' ')) {
            val qTokens = queryLower.split(whitespace).toSet()
            val lTokens = (text + " " + desc).split(whitespace).toSet()
            val overlap = qTokens.intersect(lTokens).size
            bonus += overlap * 10
        }
        return base + bonus.coerceAtMost(MAX_BONUS)
    }

    private fun whichFieldMatched(node: AccessibilityNodeInfo, queryLower: String): String {
        val text = node.text?.toString()?.lowercase().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
        val resId = node.viewIdResourceName?.lowercase().orEmpty()
        return when {
            text.isNotEmpty() && text.contains(queryLower) -> "text"
            desc.isNotEmpty() && desc.contains(queryLower) -> "contentDesc"
            resId.isNotEmpty() && resId.contains(queryLower) -> "resourceId"
            node.hintText?.toString()?.lowercase()?.contains(queryLower) == true -> "hint"
            else -> "className"
        }
    }

    private fun tokenEquals(label: String, query: String): Boolean {
        val tokens = label.split(whitespace).filter { it.isNotBlank() }
        return tokens.any { it == query }
    }

    /**
     * Matches crude Indonesian/English class-name hints: "tombol" / "button"
     * picks up ImageButton, AppCompatButton, etc.
     */
    private fun classHintMatches(cls: String, query: String): Boolean {
        return when (query) {
            "tombol", "button" -> cls.endsWith("button")
            "gambar", "image" -> cls.contains("image")
            "input", "kolom", "field" -> cls.contains("edittext")
            "tab" -> cls.contains("tab")
            "switch" -> cls.contains("switch")
            "checkbox" -> cls.contains("checkbox")
            else -> false
        }
    }

    companion object {
        private const val MAX_DEPTH = 50
        private const val MAX_BONUS = 150
        private val whitespace = Regex("\\s+")
    }
}
