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
import com.chibiclaw.data.database.MemoryCategory
import com.chibiclaw.memory.MemoryStore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class MemoryRememberTool @Inject constructor(
    private val memoryStore: MemoryStore,
) : Tool {

    override val spec = ToolSpec(
        name = "memory_remember",
        description = "Simpan fakta tentang user / kontak / habit / preferensi ke persistent memory. Pakai saat ada info penting yang perlu diingat antar-task.",
        parameters = mapOf(
            "category" to "enum: USER_PROFILE | CONTACT | HABIT | FACT | PREFERENCE",
            "key" to "string (semantic key, e.g. 'contact.budi')",
            "value" to "string (JSON-encoded value atau plain text)",
            "confidence" to "float (0-1, optional, default 1.0)",
        ),
        capability = ToolCapability(
            latencyMsRange = 100..500,
            worksOn = listOf("any"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(severity = ToolSeverity.LOW),
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        val categoryStr = call.args["category"]?.jsonPrimitive?.content?.uppercase()
        val category = runCatching { MemoryCategory.valueOf(categoryStr ?: "") }.getOrNull()
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.INVALID_ARGS,
                message = "category must be one of: ${MemoryCategory.entries.joinToString()}",
            )
        val key = call.args["key"]?.jsonPrimitive?.content ?: return ToolResult.Error(
            callId = call.callId,
            errorClass = ErrorClass.INVALID_ARGS,
            message = "key required",
        )
        val valueRaw = call.args["value"]?.toString() ?: return ToolResult.Error(
            callId = call.callId,
            errorClass = ErrorClass.INVALID_ARGS,
            message = "value required",
        )
        val confidence = call.args["confidence"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1.0f

        memoryStore.remember(
            category = category,
            key = key,
            valueJson = valueRaw,
            confidence = confidence,
        )

        return ToolResult.Success(
            callId = call.callId,
            data = JsonObject(mapOf(
                "remembered" to JsonPrimitive("$category/$key"),
                "confidence" to JsonPrimitive(confidence),
            )),
        )
    }
}
