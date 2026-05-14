package com.chibiclaw.agent.tools.impl

import com.chibiclaw.agent.tools.Tool
import com.chibiclaw.agent.tools.ToolCall
import com.chibiclaw.agent.tools.ToolCapability
import com.chibiclaw.agent.tools.ToolCost
import com.chibiclaw.agent.tools.ToolResult
import com.chibiclaw.agent.tools.ToolSafety
import com.chibiclaw.agent.tools.ToolSeverity
import com.chibiclaw.agent.tools.ToolSpec
import com.chibiclaw.data.repository.TaskRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.ZoneId
import javax.inject.Inject

/**
 * memory_infer_pattern — surface candidate habit/pattern dari history.
 *
 * Phase 7 minimal: aggregate task history (last 200 task) by hour-of-day +
 * keyword extract dari goal. LLM observe hasil aggregat lalu decide apakah
 * mau emit memory_remember HABIT.
 *
 * Phase 9 polish: dedicated LLM call dengan prompt khusus dan score per
 * candidate. Sekarang Fuu pakai output JSON ini sebagai signal di iterasi
 * berikutnya.
 */
class MemoryInferPatternTool @Inject constructor(
    private val taskRepository: TaskRepository,
) : Tool {

    override val spec = ToolSpec(
        name = "memory_infer_pattern",
        description = """
            Aggregate history task (last 200) untuk surface pattern candidate:
            jam-jam aktif user, keyword tematik, durasi rata-rata.
            Tool ini bukan keputusan akhir — Fuu reason apakah mau commit ke
            memory via memory_remember dengan kategori HABIT.
        """.trimIndent(),
        parameters = mapOf(
            "scope" to "string (optional, label scope analysis e.g. 'morning_routine')",
            "limit" to "int (optional, history limit default 200)",
        ),
        capability = ToolCapability(
            latencyMsRange = 80..400,
            worksOn = listOf("local_task_history"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(
            severity = ToolSeverity.LOW,
            reason = "Local-only aggregation",
        ),
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        val scope = call.args["scope"]?.jsonPrimitive?.content ?: "general"
        val limit = call.args["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 200

        val tasks = taskRepository.recentSnapshot(limit)

        val zone = ZoneId.systemDefault()
        val hourBuckets = IntArray(24)
        val keywords = mutableMapOf<String, Int>()
        var goalsAnalyzed = 0

        tasks.forEach { task ->
            val startedMs = task.startedAt?.toEpochMilliseconds()
                ?: task.createdAt.toEpochMilliseconds()
            val hour = java.time.Instant.ofEpochMilli(startedMs).atZone(zone).hour
            hourBuckets[hour] = hourBuckets[hour] + 1

            extractKeywords(task.goal).forEach { kw ->
                keywords[kw] = (keywords[kw] ?: 0) + 1
            }
            goalsAnalyzed++
        }

        val topHours = hourBuckets.withIndex()
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .take(5)
        val topKeywords = keywords.entries
            .sortedByDescending { it.value }
            .take(10)

        val hoursArr = buildJsonArray {
            topHours.forEach { (hour, count) ->
                add(buildJsonObject { put("hour", hour); put("count", count) })
            }
        }
        val keywordsArr = buildJsonArray {
            topKeywords.forEach { (kw, count) ->
                add(buildJsonObject { put("keyword", kw); put("count", count) })
            }
        }

        return ToolResult.Success(
            callId = call.callId,
            data = JsonObject(mapOf(
                "scope" to JsonPrimitive(scope),
                "goals_analyzed" to JsonPrimitive(goalsAnalyzed),
                "top_hours" to hoursArr,
                "top_keywords" to keywordsArr,
                "hint" to JsonPrimitive(
                    "Inspect top_hours + top_keywords untuk identify habit. " +
                        "Kalau ada pola jelas, emit memory_remember kategori HABIT.",
                ),
            )),
        )
    }

    private fun extractKeywords(text: String): List<String> {
        return text
            .lowercase()
            .split(Regex("[^a-z0-9_]+"))
            .filter { it.length >= 4 && it !in STOPWORDS }
    }

    companion object {
        private val STOPWORDS = setOf(
            "yang", "saya", "kamu", "untuk", "dengan", "saja", "udah", "yang",
            "this", "that", "with", "from", "into", "over", "your", "have",
            "fuu", "claw", "chibiclaw",
        )
    }
}
