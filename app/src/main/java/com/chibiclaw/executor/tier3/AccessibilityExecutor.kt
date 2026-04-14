package com.chibiclaw.executor.tier3

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.chibiclaw.executor.UiInteractAction
import com.chibiclaw.service.ChibiAccessibility
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityExecutor @Inject constructor(
    private val targetResolver: TargetResolver
) {

    fun perform(action: UiInteractAction): String {
        // BUG-L: the orchestrator's error check prefixes are
        // `ui_error` / `error`, but we were returning
        // "accessibility_not_connected" which fell through as success.
        // Rename so the FastPath success check actually catches it,
        // and include a human-actionable hint in the body.
        if (!ChibiAccessibility.isConnected()) {
            Log.w(TAG, "Accessibility service not connected — cannot perform ${action.action}")
            return "ui_error: accessibility service belum aktif. Buka Settings → Accessibility → ChibiClaw → aktifkan, lalu coba lagi."
        }

        // Global-action shortcuts don't need a root node.
        when (action.action.lowercase()) {
            "back" -> return globalActionResult(
                AccessibilityService.GLOBAL_ACTION_BACK, "back_performed"
            )
            "home" -> return globalActionResult(
                AccessibilityService.GLOBAL_ACTION_HOME, "home_performed"
            )
            "recents" -> return globalActionResult(
                AccessibilityService.GLOBAL_ACTION_RECENTS, "recents_performed"
            )
            "notifications" -> return globalActionResult(
                AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "notifications_performed"
            )
            "quick_settings" -> return globalActionResult(
                AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "quick_settings_performed"
            )
            "lock_screen" -> return globalActionResult(
                AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "lock_screen_performed"
            )
            "split_screen" -> return globalActionResult(
                AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN, "split_screen_performed"
            )
            "power_dialog" -> return globalActionResult(
                AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, "power_dialog_performed"
            )
        }

        val root = ChibiAccessibility.getRootNode()
            ?: return "ui_error: tidak bisa membaca layar sekarang (root node null — coba lagi)."

        return when (action.action.lowercase()) {
            "click" -> click(root, action.target)
            "type" -> type(root, action.target, action.text)
            "scroll_down" -> scroll(root, action.target, false)
            "scroll_up" -> scroll(root, action.target, true)
            else -> "unknown_action: ${action.action}"
        }
    }

    private fun globalActionResult(action: Int, successLabel: String): String {
        val ok = ChibiAccessibility.getInstance()?.performGlobalAction(action) == true
        return if (ok) successLabel else "ui_error: global_action_$successLabel failed"
    }

    private fun click(root: AccessibilityNodeInfo, target: String): String {
        val match = targetResolver.resolve(root, target)
            ?: return "node_not_found: $target — coba vision_analyze(find_element) untuk cari koordinat visual lalu gesture(kind=\"tap_coord\")"
        // Walk up to a clickable ancestor if the best match is itself not
        // clickable (common when text sits inside a LinearLayout wrapper).
        val clickTarget = findClickableAncestor(match.node) ?: match.node
        val success = clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return if (success) "click_success: $target (matched_on=${match.matchedOn})"
               else "click_failed: $target"
    }

    private fun type(root: AccessibilityNodeInfo, target: String, text: String): String {
        val match = targetResolver.resolve(root, target)
        val node = match?.node ?: findEditableNode(root)
            ?: return "editable_not_found: $target"
        node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return if (success) "type_success" else "type_failed"
    }

    private fun scroll(root: AccessibilityNodeInfo, target: String, up: Boolean): String {
        val node = if (target.isEmpty()) {
            findScrollableNode(root)
        } else {
            // Try to find the requested target first, then walk up looking
            // for a scrollable ancestor (e.g. text row → RecyclerView parent).
            val resolved = targetResolver.resolve(root, target)?.node
            findScrollableAncestor(resolved) ?: findScrollableNode(root)
        } ?: return "scrollable_not_found"
        val action = if (up) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                     else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        val success = node.performAction(action)
        return if (success) "scroll_success" else "scroll_failed"
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops < 6) {
            if (current.isClickable) return current
            current = current.parent
            hops++
        }
        return null
    }

    private fun findScrollableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops < 10) {
            if (current.isScrollable) return current
            current = current.parent
            hops++
        }
        return null
    }

    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isEditable) return root
        for (i in 0 until root.childCount) {
            findEditableNode(root.getChild(i) ?: continue)?.let { return it }
        }
        return null
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isScrollable) return root
        for (i in 0 until root.childCount) {
            findScrollableNode(root.getChild(i) ?: continue)?.let { return it }
        }
        return null
    }

    companion object {
        private const val TAG = "AccessibilityExecutor"
    }
}
