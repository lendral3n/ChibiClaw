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
import kotlinx.serialization.json.jsonObject
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
            "value" to "object (JSON-serializable, e.g. {\"name\":\"Budi\",\"phone\":\"+62...\"}) atau string",
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

        // Value bisa: JsonObject (structured fact) atau JsonPrimitive (plain string).
        // Simpan sebagai string canonical JSON, supaya consumer (memory_recall) bisa
        // parse balik tanpa ambiguity.
        val valueElement = call.args["value"] ?: return ToolResult.Error(
            callId = call.callId,
            errorClass = ErrorClass.INVALID_ARGS,
            message = "value required",
        )
        val valueJson = when {
            // JsonObject → langsung serialize tree (no escape, no .toString() chained quotes)
            valueElement is JsonObject -> valueElement.toString()
            // JsonPrimitive string → simpan sebagai JSON string literal supaya konsisten
            valueElement is JsonPrimitive && valueElement.isString -> valueElement.toString()
            // JsonPrimitive number/boolean → simpan as-is (akan jadi JSON literal valid)
            valueElement is JsonPrimitive -> valueElement.content
            // JsonArray atau lainnya → toString (jsonArray.toString() menghasilkan JSON valid)
            else -> valueElement.toString()
        }

        val confidence = call.args["confidence"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1.0f

        memoryStore.remember(
            category = category,
            key = key,
            valueJson = valueJson,
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
