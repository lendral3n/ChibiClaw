package com.chibiclaw.perception.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.chibiclaw.service.ChibiAccessibility
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UiTreeScraper @Inject constructor() {

    fun getRootNode(): AccessibilityNodeInfo? = ChibiAccessibility.getRootNode()

    fun isAvailable(): Boolean = ChibiAccessibility.isConnected()

    fun countInteractiveNodes(root: AccessibilityNodeInfo? = getRootNode()): Int {
        if (root == null) return 0
        var count = if (root.isClickable || root.isEditable || root.isScrollable) 1 else 0
        for (i in 0 until root.childCount) {
            count += countInteractiveNodes(root.getChild(i))
        }
        return count
    }
}
