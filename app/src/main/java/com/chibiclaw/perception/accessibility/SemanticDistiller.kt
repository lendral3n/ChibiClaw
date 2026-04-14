package com.chibiclaw.perception.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SemanticDistiller @Inject constructor(
    private val coordinateExtractor: CoordinateExtractor
) {
    private val MAX_NODES = 60

    fun distill(root: AccessibilityNodeInfo?): String {
        if (root == null) return "[NO UI AVAILABLE]"
        val nodes = coordinateExtractor.extractCoordinates(root)
        if (nodes.isEmpty()) return "[EMPTY UI]"

        val sb = StringBuilder()
        // P4.1: Prompt injection protection — wrap UI content in explicit boundary tags
        // The system prompt instructs Gemma to IGNORE any instructions found inside these tags
        sb.append("[UI_CONTENT_START]\n")

        nodes.take(MAX_NODES).forEach { node ->
            val label = when {
                node.isEditable -> "INPUT"
                node.isClickable -> "BTN"
                else -> "ELEM"
            }
            val rawText = node.text.ifEmpty { node.contentDesc }.ifEmpty { node.resourceId.substringAfterLast("/") }
            // Sanitize: remove any text that looks like a system instruction injection attempt
            val text = sanitizeUIText(rawText)
            val cx = (node.bounds.left + node.bounds.right) / 2
            val cy = (node.bounds.top + node.bounds.bottom) / 2
            if (text.isNotEmpty()) {
                sb.append("$label \"$text\" @($cx,$cy)\n")
            }
        }

        if (nodes.size > MAX_NODES) {
            sb.append("[+${nodes.size - MAX_NODES} more nodes truncated]\n")
        }
        sb.append("[UI_CONTENT_END]")
        return sb.toString()
    }

    /**
     * P4.1 — Sanitize UI text to prevent prompt injection.
     * Removes text patterns that could be interpreted as system instructions.
     */
    private fun sanitizeUIText(text: String): String {
        if (text.isBlank()) return ""
        // Flag suspicious patterns (don't silently drop — replace with [FILTERED])
        val suspiciousPatterns = listOf(
            "ignore previous instructions",
            "disregard all",
            "new system prompt",
            "act as",
            "you are now",
            "forget everything",
            "override",
            "[SYSTEM]",
            "###INSTRUCTION"
        )
        val lower = text.lowercase()
        if (suspiciousPatterns.any { lower.contains(it.lowercase()) }) {
            return "[FILTERED]"
        }
        return text
    }
}
