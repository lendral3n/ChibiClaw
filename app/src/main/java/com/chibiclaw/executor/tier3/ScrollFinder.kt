package com.chibiclaw.executor.tier3

import android.view.accessibility.AccessibilityNodeInfo
import com.chibiclaw.service.ChibiAccessibility
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1.4 — scroll-until-found.
 *
 * `ui_interact(scroll_down)` is fine when you know the element is one
 * swipe away, but most real user flows look like
 *   "scroll TikTok for-you until a cat video, then like it"
 * which needs a bounded retry loop that (a) scrolls, (b) re-reads the
 * tree, (c) checks via [TargetResolver] whether the target appeared.
 *
 * [scrollUntilFound] also short-circuits when the same-looking tree shows
 * up twice in a row so we don't keep scrolling at the very bottom of a
 * list with no new content.
 */
@Singleton
class ScrollFinder @Inject constructor(
    private val targetResolver: TargetResolver
) {

    /**
     * Scrolls a scrollable container repeatedly until [query] is found
     * or [maxScrolls] swipes have been attempted.
     *
     * @return human-readable status: `found_at_scroll_N`, `not_found_after_N_scrolls`,
     *         `no_scrollable_container`, `end_of_list`.
     */
    suspend fun scrollUntilFound(
        query: String,
        maxScrolls: Int = 10,
        scrollUp: Boolean = false,
        delayMs: Long = 350L
    ): String {
        if (query.isBlank()) return "scroll_until_found: empty query"

        repeat(maxScrolls + 1) { attempt ->
            val root = ChibiAccessibility.getRootNode() ?: return "scroll_until_found: root_null"
            val match = targetResolver.resolve(root, query)
            if (match != null) {
                return "found_at_scroll_$attempt (matched_on=${match.matchedOn})"
            }
            if (attempt == maxScrolls) return "not_found_after_${maxScrolls}_scrolls"

            val scrollable = findScrollable(root)
                ?: return "no_scrollable_container"
            val beforeSig = treeSignature(root)

            val action = if (scrollUp) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                         else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            val ok = scrollable.performAction(action)
            if (!ok) return "scroll_rejected_at_attempt_$attempt"
            delay(delayMs)

            val after = ChibiAccessibility.getRootNode() ?: return "root_null_after_scroll"
            if (treeSignature(after) == beforeSig) {
                return "end_of_list_at_attempt_$attempt"
            }
        }
        return "not_found_after_${maxScrolls}_scrolls"
    }

    /**
     * Waits up to [timeoutMs] for [query] to appear in the accessibility
     * tree. Useful as a cheap "did the next screen load yet?" check after
     * a click or intent.
     */
    suspend fun waitForElement(
        query: String,
        timeoutMs: Long = 5_000L,
        pollMs: Long = 200L
    ): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        var polls = 0
        while (System.currentTimeMillis() < deadline) {
            val root = ChibiAccessibility.getRootNode()
            if (root != null) {
                val match = targetResolver.resolve(root, query)
                if (match != null) return "found_after_${polls}_polls"
            }
            polls++
            delay(pollMs)
        }
        return "not_found_after_${timeoutMs}ms"
    }

    private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            findScrollable(node.getChild(i))?.let { return it }
        }
        return null
    }

    /**
     * Cheap hash of the tree's visible text — used to detect "scrolled
     * but nothing moved" (end of list) without allocating a full UI map.
     */
    private fun treeSignature(root: AccessibilityNodeInfo?): Int {
        if (root == null) return 0
        val sb = StringBuilder()
        walkText(root, sb, 0)
        return sb.toString().hashCode()
    }

    private fun walkText(node: AccessibilityNodeInfo, out: StringBuilder, depth: Int) {
        if (depth > 20) return
        node.text?.let { out.append(it).append('|') }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { walkText(it, out, depth + 1) }
        }
    }
}
