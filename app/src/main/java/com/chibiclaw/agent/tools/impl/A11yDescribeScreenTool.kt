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

class A11yDescribeScreenTool @Inject constructor() : Tool {

    override val spec = ToolSpec(
        name = "a11y_describe_screen",
        description = """
            Return text representation of accessibility tree (app foreground).
            Format: class + #resource_id + "text" + [description] per node, indented by depth.
            Pakai untuk locate selector sebelum a11y_click / a11y_type, atau untuk debug.
        """.trimIndent(),
        parameters = mapOf(
            "max_depth" to "int (optional, default 5)",
        ),
        capability = ToolCapability(
            latencyMsRange = 100..400,
            worksOn = listOf("accessibility_exposed_apps"),
            knownFail = listOf("anti-accessibility_apps (TikTok, WhatsApp, IG, Shopee)"),
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
            message = "Accessibility Service belum aktif",
        )
        val maxDepth = call.args["max_depth"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5
        val tree = service.treeWalker.describeScreen(maxDepth)
        return ToolResult.Success(
            callId = call.callId,
            data = JsonObject(mapOf(
                "tree" to JsonPrimitive(tree.take(4000)),
                "lines" to JsonPrimitive(tree.lineSequence().count()),
            )),
        )
    }
}
