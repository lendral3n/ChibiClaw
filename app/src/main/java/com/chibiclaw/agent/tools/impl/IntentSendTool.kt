package com.chibiclaw.agent.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.chibiclaw.agent.tools.ErrorClass
import com.chibiclaw.agent.tools.Tool
import com.chibiclaw.agent.tools.ToolCall
import com.chibiclaw.agent.tools.ToolCapability
import com.chibiclaw.agent.tools.ToolCost
import com.chibiclaw.agent.tools.ToolResult
import com.chibiclaw.agent.tools.ToolSafety
import com.chibiclaw.agent.tools.ToolSeverity
import com.chibiclaw.agent.tools.ToolSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class IntentSendTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {

    override val spec = ToolSpec(
        name = "intent_send",
        description = """
            Send Android Intent (implicit atau explicit). Generik — bisa untuk dial,
            navigate, share text, open URL, dll.
            Contoh action:
              - android.intent.action.DIAL + data tel:+62812...
              - android.intent.action.VIEW + data https://...
              - android.intent.action.SEND + extras EXTRA_TEXT
        """.trimIndent(),
        parameters = mapOf(
            "action" to "string (e.g. 'android.intent.action.DIAL', 'android.intent.action.VIEW')",
            "data" to "string (URI, optional)",
            "type" to "string (MIME type, optional)",
        ),
        capability = ToolCapability(
            latencyMsRange = 200..600,
            worksOn = listOf("any_app_intent_filter"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(
            severity = ToolSeverity.MEDIUM,
            reason = "Generic intent — bisa trigger external action (call, send, navigate)",
        ),
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        val action = call.args["action"]?.jsonPrimitive?.content
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.INVALID_ARGS,
                message = "action arg required",
            )
        val data = call.args["data"]?.jsonPrimitive?.content
        val type = call.args["type"]?.jsonPrimitive?.content

        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (data != null) {
                setData(Uri.parse(data))
            }
            if (type != null) {
                setType(type)
            }
        }

        return runCatching {
            context.startActivity(intent)
            ToolResult.Success(
                callId = call.callId,
                data = JsonObject(mapOf(
                    "action" to JsonPrimitive(action),
                    "data" to JsonPrimitive(data ?: ""),
                )),
            )
        }.getOrElse { t ->
            ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.NOT_AVAILABLE,
                message = "Intent send failed: ${t.message}",
            )
        }
    }
}
