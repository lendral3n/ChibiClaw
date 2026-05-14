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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * memory_list_by_category — enumerate memory records dalam satu kategori.
 *
 * Phase 7: sort by accessCount DESC, filter by minConfidence (default 0.2).
 * LLM pakai untuk: "list semua kontak yang Fuu tau", "habit user yang sudah
 * di-confirm", dll.
 */
class MemoryListByCategoryTool @Inject constructor(
    private val store: MemoryStore,
) : Tool {

    override val spec = ToolSpec(
        name = "memory_list_by_category",
        description = """
            List records dalam satu kategori memory: USER_PROFILE / CONTACT /
            HABIT / FACT / PREFERENCE. Result urut access_count DESC, filtered
            by min_confidence. Cocok untuk overview tanpa semantic search.
        """.trimIndent(),
        parameters = mapOf(
            "category" to "enum: USER_PROFILE | CONTACT | HABIT | FACT | PREFERENCE",
            "min_confidence" to "float (optional, default 0.2)",
            "limit" to "int (optional, default 50)",
        ),
        capability = ToolCapability(
            latencyMsRange = 30..150,
            worksOn = listOf("local_memory_store"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(
            severity = ToolSeverity.LOW,
            reason = "Local-only memory; no PII transit",
        ),
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        val categoryStr = call.args["category"]?.jsonPrimitive?.content?.uppercase()
            ?: return ToolResult.Error(call.callId, ErrorClass.INVALID_ARGS, "category arg required")
        val category = runCatching { MemoryCategory.valueOf(categoryStr) }.getOrNull()
            ?: return ToolResult.Error(call.callId, ErrorClass.INVALID_ARGS, "category invalid: $categoryStr")
        val minConf = call.args["min_confidence"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0.2f
        val limit = call.args["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 50

        val records = store.listByCategory(category)
            .filter { it.confidence >= minConf }
            .sortedByDescending { it.accessCount }
            .take(limit)

        val array = buildJsonArray {
            records.forEach { r ->
                add(buildJsonObject {
                    put("key", r.key)
                    put("value", r.valueJson.take(200))
                    put("confidence", r.confidence)
                    put("access_count", r.accessCount)
                    put("last_accessed_at", r.lastAccessedAt.toString())
                })
            }
        }
        return ToolResult.Success(
            callId = call.callId,
            data = JsonObject(mapOf(
                "category" to JsonPrimitive(category.name),
                "count" to JsonPrimitive(records.size),
                "records" to array,
            )),
        )
    }
}
