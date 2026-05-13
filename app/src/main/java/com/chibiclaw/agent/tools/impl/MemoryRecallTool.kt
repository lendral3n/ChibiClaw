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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

class MemoryRecallTool @Inject constructor(
    private val memoryStore: MemoryStore,
) : Tool {

    override val spec = ToolSpec(
        name = "memory_recall",
        description = "Semantic search persistent memory. Return top-K records yang relevant dengan query.",
        parameters = mapOf(
            "query" to "string (natural language query)",
            "category" to "enum (optional, filter by category)",
            "top_k" to "int (optional, default 5)",
            "min_similarity" to "float (optional, default 0.3)",
        ),
        capability = ToolCapability(
            latencyMsRange = 200..800,
            worksOn = listOf("any"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(severity = ToolSeverity.LOW),
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        val query = call.args["query"]?.jsonPrimitive?.content ?: return ToolResult.Error(
            callId = call.callId,
            errorClass = ErrorClass.INVALID_ARGS,
            message = "query required",
        )
        val categoryStr = call.args["category"]?.jsonPrimitive?.content?.uppercase()
        val category = if (categoryStr.isNullOrBlank()) null
            else runCatching { MemoryCategory.valueOf(categoryStr) }.getOrNull()
        val topK = call.args["top_k"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5
        val minSim = call.args["min_similarity"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0.3f

        val hits = memoryStore.recall(query, category, topK, minSim)

        val recordsJson = buildJsonArray {
            hits.forEach { hit ->
                add(buildJsonObject {
                    put("id", hit.record.id)
                    put("category", hit.record.category.name)
                    put("key", hit.record.key)
                    put("value", hit.record.valueJson)
                    put("confidence", hit.record.confidence)
                    put("similarity", hit.similarity)
                })
            }
        }

        return ToolResult.Success(
            callId = call.callId,
            data = JsonObject(mapOf(
                "count" to JsonPrimitive(hits.size),
                "records" to recordsJson,
            )),
        )
    }
}
