package com.chibiclaw.agent.tools.impl

import com.chibiclaw.agent.tools.ErrorClass
import com.chibiclaw.agent.tools.Tool
import com.chibiclaw.agent.tools.ToolCall
import com.chibiclaw.agent.tools.ToolCapability
import com.chibiclaw.agent.tools.ToolCost
import com.chibiclaw.agent.tools.ToolResult
import com.chibiclaw.agent.tools.ToolSafety
import com.chibiclaw.agent.tools.ToolSeverity
import com.chibiclaw.agent.tools.ToolSpec
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class WaitTool @Inject constructor() : Tool {

    override val spec = ToolSpec(
        name = "wait",
        description = "Pause N detik sebelum lanjut. Pakai saat butuh kasih waktu app load/animate.",
        parameters = mapOf("seconds" to "float (0.1-60.0)"),
        capability = ToolCapability(
            latencyMsRange = 100..60_000,
            worksOn = listOf("any"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(severity = ToolSeverity.LOW),
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        val seconds = call.args["seconds"]?.jsonPrimitive?.contentOrNull()?.toFloatOrNull()
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.INVALID_ARGS,
                message = "seconds must be float",
            )
        val ms = (seconds.coerceIn(0.1f, 60f) * 1000).toLong()
        delay(ms)
        return ToolResult.Success(
            callId = call.callId,
            data = JsonObject(mapOf("waited_ms" to JsonPrimitive(ms))),
        )
    }
}

private fun JsonPrimitive.contentOrNull(): String? = content
