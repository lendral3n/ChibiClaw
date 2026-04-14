package com.chibiclaw.perception.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoordinateExtractor @Inject constructor() {

    data class NodeCoordinate(
        val text: String,
        val contentDesc: String,
        val resourceId: String,
        val bounds: Rect,
        val isClickable: Boolean,
        val isEditable: Boolean
    )

    fun extractCoordinates(root: AccessibilityNodeInfo?): List<NodeCoordinate> {
        if (root == null) return emptyList()
        val result = mutableListOf<NodeCoordinate>()
        traverse(root, result)
        return result
    }

    private fun traverse(node: AccessibilityNodeInfo, result: MutableList<NodeCoordinate>) {
        if (node.isClickable || node.isEditable || node.isScrollable) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                result.add(
                    NodeCoordinate(
                        text = node.text?.toString() ?: "",
                        contentDesc = node.contentDescription?.toString() ?: "",
                        resourceId = node.viewIdResourceName ?: "",
                        bounds = bounds,
                        isClickable = node.isClickable,
                        isEditable = node.isEditable
                    )
                )
            }
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { traverse(it, result) }
        }
    }
}
