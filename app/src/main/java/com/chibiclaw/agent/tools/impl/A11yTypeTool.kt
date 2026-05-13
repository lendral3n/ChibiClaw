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

class A11yTypeTool @Inject constructor() : Tool {

    override val spec = ToolSpec(
        name = "a11y_type",
        description = """
            Type teks ke focused EditText via Accessibility ACTION_SET_TEXT.
            Pakai setelah field text ter-focus (mis. via a11y_click sebelumnya).
            Limitations: tidak bisa input ke secure field (password, OTP).
        """.trimIndent(),
        parameters = mapOf("text" to "string (teks yang mau di-type)"),
        capability = ToolCapability(
            latencyMsRange = 100..300,
            worksOn = listOf("accessibility_text_input"),
            knownFail = listOf("secure_password_fields", "OTP_fields"),
            requiresPermission = listOf("ACCESSIBILITY_SERVICE"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(
            severity = ToolSeverity.MEDIUM,
            reason = "Types arbitrary text into focused field, mungkin sensitive context",
        ),
    )

    override fun isAvailable(): Boolean = ChibiAccessibilityService.isConnected()

    override suspend fun execute(call: ToolCall): ToolResult {
        val service = ChibiAccessibilityService.instance() ?: return ToolResult.Error(
            callId = call.callId,
            errorClass = ErrorClass.PERMISSION_DENIED,
            message = "Accessibility Service belum aktif",
        )
        val text = call.args["text"]?.jsonPrimitive?.content
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.INVALID_ARGS,
                message = "text arg required",
            )
        val ok = service.treeWalker.typeIntoFocused(text)
        return if (ok) {
            ToolResult.Success(
                callId = call.callId,
                data = JsonObject(mapOf("typed_length" to JsonPrimitive(text.length))),
            )
        } else {
            ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.SELECTOR_NOT_FOUND,
                message = "Tidak ada EditText focused — pakai a11y_click ke input dulu",
                recoveryHint = "Coba a11y_describe_screen untuk locate field, lalu a11y_click",
            )
        }
    }
}
