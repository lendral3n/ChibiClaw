package com.chibiclaw.agent.tools.impl

import com.chibiclaw.accessibility.ChibiAccessibilityService
import com.chibiclaw.accessibility.a11y.ScrollDirection
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

class A11yScrollTool @Inject constructor() : Tool {

    override val spec = ToolSpec(
        name = "a11y_scroll",
        description = "Scroll node terdekat yang scrollable di foreground app. Direction: UP, DOWN, LEFT, RIGHT.",
        parameters = mapOf("direction" to "enum: UP | DOWN | LEFT | RIGHT"),
        capability = ToolCapability(
            latencyMsRange = 200..600,
            worksOn = listOf("accessibility_scrollable"),
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
        val directionStr = call.args["direction"]?.jsonPrimitive?.content?.uppercase()
        val direction = runCatching { ScrollDirection.valueOf(directionStr ?: "") }.getOrNull()
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.INVALID_ARGS,
                message = "direction must be UP, DOWN, LEFT, or RIGHT",
            )

        val ok = service.treeWalker.scroll(direction)
        return if (ok) {
            ToolResult.Success(
                callId = call.callId,
                data = JsonObject(mapOf("scrolled" to JsonPrimitive(direction.name))),
            )
        } else {
            ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.SELECTOR_NOT_FOUND,
                message = "Tidak ada node scrollable di layar saat ini",
            )
        }
    }
}
