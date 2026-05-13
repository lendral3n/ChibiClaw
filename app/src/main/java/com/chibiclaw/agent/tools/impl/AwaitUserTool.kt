package com.chibiclaw.agent.tools.impl

import com.chibiclaw.agent.tools.Tool
import com.chibiclaw.agent.tools.ToolCall
import com.chibiclaw.agent.tools.ToolCapability
import com.chibiclaw.agent.tools.ToolCost
import com.chibiclaw.agent.tools.ToolResult
import com.chibiclaw.agent.tools.ToolSafety
import com.chibiclaw.agent.tools.ToolSeverity
import com.chibiclaw.agent.tools.ToolSpec
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/**
 * await_user — bukan literal tool execute, tapi LLM bisa explicit emit ini
 * untuk request clarification. AgentRuntime handle via outcome `next: await_user`
 * (di parser). Tool ini ada di catalog untuk completeness signaling ke LLM.
 *
 * Kalau LLM emit `tool_calls: [{tool: await_user, args: {question: ...}}]` instead
 * of `next: await_user`, kita treat sama: mark task AWAITING_USER.
 */
class AwaitUserTool @Inject constructor() : Tool {

    override val spec = ToolSpec(
        name = "await_user",
        description = "Stop task, minta clarification dari user. Pakai saat task ambigu atau butuh detail.",
        parameters = mapOf("question" to "string"),
        capability = ToolCapability(
            latencyMsRange = 1..100,
            worksOn = listOf("any"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(severity = ToolSeverity.LOW),
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        // Idempotent — actual await handled di AgentRuntime via parser hint.
        return ToolResult.Success(callId = call.callId, data = JsonObject(emptyMap()))
    }
}
