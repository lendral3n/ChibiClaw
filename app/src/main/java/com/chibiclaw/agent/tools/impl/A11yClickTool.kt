package com.chibiclaw.agent.tools.impl

import com.chibiclaw.accessibility.ChibiAccessibilityService
import com.chibiclaw.agent.tools.ErrorClass
import com.chibiclaw.agent.tools.Tool
import com.chibiclaw.agent.tools.ToolCall
import com.chibiclaw.agent.tools.ToolCapability
import com.chibiclaw.agent.tools.ToolCost
import com.chibiclaw.agent.tools.ToolResult
import com.chibiclaw.agent.tools.ToolSafety
import com.chibiclaw.agent.tools.ToolSeverity
import com.chibiclaw.agent.tools.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class A11yClickTool @Inject constructor() : Tool {

    override val spec = ToolSpec(
        name = "a11y_click",
        description = """
            Click element di app foreground via Accessibility Service.
            Selector matching lenient: cek text, contentDescription, resource_id, className.
            Known fail: TikTok, WhatsApp, Instagram, Tokopedia, Shopee, banking apps —
            mereka block accessibility automation. Fallback ke vision_tap (Phase 5).
        """.trimIndent(),
        parameters = mapOf(
            "selector" to "string (text/desc/resource_id partial match, case-insensitive)",
            "app_package" to "string (optional, filter target app)",
        ),
        capability = ToolCapability(
            latencyMsRange = 150..600,
            worksOn = listOf("accessibility_exposed_apps"),
            knownFail = listOf("TikTok", "WhatsApp", "Instagram", "Tokopedia", "Shopee", "banking"),
            requiresPermission = listOf("ACCESSIBILITY_SERVICE"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(severity = ToolSeverity.LOW),
    )

    override fun isAvailable(): Boolean = ChibiAccessibilityService.isConnected()

    override suspend fun execute(call: ToolCall): ToolResult {
        val service = ChibiAccessibilityService.instance() ?: return ToolResult.Error(
            callId = call.callId,
            errorClass = ErrorClass.PERMISSION_DENIED,
            message = "Accessibility Service belum aktif — user wajib enable di Settings",
            recoveryHint = "Minta user buka Settings → Accessibility → ChibiClaw → ON",
        )
        val selector = call.args["selector"]?.jsonPrimitive?.content
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.INVALID_ARGS,
                message = "selector arg required",
            )
        val appPackage = call.args["app_package"]?.jsonPrimitive?.content

        val node = service.treeWalker.findNode(selector, appPackage)
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.SELECTOR_NOT_FOUND,
                message = "Node '$selector' tidak ketemu di accessibility tree",
                recoveryHint = "Coba a11y_describe_screen dulu untuk lihat layout, atau fallback vision_tap",
            )
        val clicked = service.treeWalker.click(node)
        return if (clicked) {
            ToolResult.Success(
                callId = call.callId,
                data = JsonObject(mapOf("clicked_selector" to JsonPrimitive(selector))),
            )
        } else {
            ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.SELECTOR_NOT_FOUND,
                message = "Node ketemu tapi tidak ada clickable ancestor",
            )
        }
    }
}
