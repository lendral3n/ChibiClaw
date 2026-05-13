package com.chibiclaw.accessibility.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred
import timber.log.Timber

/**
 * Helper untuk traverse accessibility node tree + perform actions.
 *
 * Phase 3: cukup untuk a11y_click, a11y_type, a11y_describe, a11y_scroll.
 * Phase 5 polish: visual grounding hybrid (a11y + vision overlay).
 */
class A11yTreeWalker(private val service: AccessibilityService) {

    /**
     * Search root window untuk node yang match selector. Selector matching
     * lenient: cek text, contentDescription, viewIdResourceName, className.
     */
    fun findNode(selector: String, appPackage: String? = null): AccessibilityNodeInfo? {
        val needle = selector.trim().lowercase()
        val roots = service.windows
            .filter { appPackage == null || it.root?.packageName?.toString() == appPackage }
            .mapNotNull { it.root }

        for (root in roots) {
            val found = walk(root) { node -> matches(node, needle) }
            if (found != null) return found
        }
        // Fallback: try service.rootInActiveWindow
        val activeRoot = service.rootInActiveWindow ?: return null
        return walk(activeRoot) { node -> matches(node, needle) }
    }

    /**
     * Recursively walk node tree, return first node yang predicate true.
     */
    private fun walk(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = walk(child, predicate)
            if (result != null) return result
        }
        return null
    }

    private fun matches(node: AccessibilityNodeInfo, needle: String): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val cls = node.className?.toString()?.lowercase() ?: ""
        return text.contains(needle) ||
            desc.contains(needle) ||
            viewId.contains(needle) ||
            (cls.endsWith(needle))
    }

    /**
     * Build flat text representation of node tree (untuk a11y_describe_screen).
     */
    fun describeScreen(maxDepth: Int = 5): String {
        val root = service.rootInActiveWindow ?: return "(no active window)"
        val sb = StringBuilder()
        describe(root, sb, depth = 0, maxDepth = maxDepth)
        return sb.toString()
    }

    private fun describe(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        val indent = "  ".repeat(depth)
        val text = node.text?.toString()?.takeIf { it.isNotBlank() }
        val desc = node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        val viewId = node.viewIdResourceName?.substringAfterLast('/')
        val cls = node.className?.toString()?.substringAfterLast('.')
        val parts = listOfNotNull(
            cls,
            viewId?.let { "#$it" },
            text?.let { "\"$it\"" },
            desc?.let { "[$it]" },
            "${if (node.isClickable) "clickable" else ""}".takeIf { node.isClickable },
            "${if (node.isFocused) "focused" else ""}".takeIf { node.isFocused },
        )
        if (parts.isNotEmpty()) {
            sb.append(indent).append(parts.joinToString(" ")).append('\n')
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            describe(child, sb, depth + 1, maxDepth)
        }
    }

    /**
     * Perform click via accessibility action.
     */
    fun click(node: AccessibilityNodeInfo): Boolean {
        // Cari clickable ancestor kalau node sendiri tidak clickable
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) {
            target = target.parent
        }
        if (target == null) {
            Timber.w("No clickable ancestor for selector")
            return false
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * Type text ke focused EditText.
     */
    fun typeIntoFocused(text: String): Boolean {
        val focused = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: service.rootInActiveWindow?.let { walk(it) { n -> n.isEditable && n.isFocused } }
            ?: service.rootInActiveWindow?.let { walk(it) { n -> n.isEditable } }
        if (focused == null) {
            Timber.w("No editable focused node")
            return false
        }
        val arguments = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    /**
     * Scroll node terdekat yang scrollable.
     */
    fun scroll(direction: ScrollDirection): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val scrollable = walk(root) { it.isScrollable } ?: return false
        val action = when (direction) {
            ScrollDirection.DOWN, ScrollDirection.RIGHT -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            ScrollDirection.UP, ScrollDirection.LEFT -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return scrollable.performAction(action)
    }

    /**
     * Tap koordinat (untuk vision_tap Phase 5; juga fallback kalau a11y node
     * tidak clickable). Suspend supaya bisa await gesture completion.
     */
    suspend fun tapAt(x: Int, y: Int): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        val ok = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(description: GestureDescription?) {
                    deferred.complete(true)
                }
                override fun onCancelled(description: GestureDescription?) {
                    deferred.complete(false)
                }
            },
            null,
        )
        if (!ok) {
            deferred.complete(false)
        }
        return deferred.await()
    }
}

enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }
