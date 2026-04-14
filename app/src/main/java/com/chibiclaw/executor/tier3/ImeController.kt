package com.chibiclaw.executor.tier3

import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.chibiclaw.service.ChibiAccessibility
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1.5 — IME (keyboard) helpers that avoid sending raw key events.
 *
 * AccessibilityService can't simulate hardware keystrokes on Android 14+
 * without the signature-only INJECT_EVENTS permission. Instead we:
 *  - press Enter by calling `ACTION_IME_ENTER` on the focused editable,
 *    which is the framework-supported way to "submit" a TextInputLayout,
 *  - dismiss the keyboard via `GLOBAL_ACTION_BACK` when we're focused
 *    inside an EditText (Back collapses the IME before it navigates),
 *  - append text to an existing EditText by reading its current value,
 *    concatenating the new fragment, and calling `ACTION_SET_TEXT`.
 *
 * Every method returns a short status string so the execution router can
 * relay it back to Gemma as tool output.
 */
@Singleton
class ImeController @Inject constructor() {

    fun pressEnter(): String {
        val svc = ChibiAccessibility.getInstance() ?: return "ime_error: not_connected"
        val focused = findFocusedEditable(svc.rootInActiveWindow)
            ?: return "ime_error: no_focused_input"
        // ACTION_IME_ENTER is R (API 30+). On older devices we fall back to
        // dispatching a click at the focus which most IMEs interpret as
        // "search"/"send" if the editor has an action label set.
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            focused.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
            )
        } else {
            focused.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        return if (ok) "enter_pressed" else "enter_failed"
    }

    fun dismissKeyboard(): String {
        val svc = ChibiAccessibility.getInstance() ?: return "ime_error: not_connected"
        // Back on Android collapses the soft keyboard WITHOUT navigating
        // away as long as an IME is open. We call the framework path.
        val ok = svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        return if (ok) "keyboard_dismissed" else "dismiss_failed"
    }

    fun appendToFocused(text: String): String {
        if (text.isEmpty()) return "ime_noop: empty_text"
        val svc = ChibiAccessibility.getInstance() ?: return "ime_error: not_connected"
        val focused = findFocusedEditable(svc.rootInActiveWindow)
            ?: return "ime_error: no_focused_input"
        val current = focused.text?.toString().orEmpty()
        val merged = current + text
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                merged
            )
        }
        val ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return if (ok) "append_success" else "append_failed"
    }

    fun clearFocused(): String {
        val svc = ChibiAccessibility.getInstance() ?: return "ime_error: not_connected"
        val focused = findFocusedEditable(svc.rootInActiveWindow)
            ?: return "ime_error: no_focused_input"
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        val ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return if (ok) "clear_success" else "clear_failed"
    }

    private fun findFocusedEditable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isFocused && root.isEditable) return root
        val direct = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (direct != null && direct.isEditable) return direct
        return walkForEditable(root, 0)
    }

    private fun walkForEditable(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > 30) return null
        if (node.isEditable && node.isFocused) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walkForEditable(child, depth + 1)?.let { return it }
        }
        return null
    }

}
