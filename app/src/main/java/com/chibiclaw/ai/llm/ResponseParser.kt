package com.chibiclaw.ai.llm

import com.chibiclaw.agent.tools.ToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/**
 * Parse raw LLM response → LlmOutcome.
 *
 * Hierarchy fallback:
 *   1. Strict JSON
 *   2. JSON within markdown fence (```json ... ```)
 *   3. Tag-based extraction
 *   4. Fallback: treat as Reasoning(text)
 */
object ResponseParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(raw: String): LlmOutcome {
        // 1. Strict JSON
        runCatching {
            val element = json.parseToJsonElement(raw)
            if (element is JsonObject) return parseJsonOutcome(element)
        }

        // 2. Fenced JSON
        val fenced = Regex("```(?:json)?\\s*(\\{[\\s\\S]+?\\})\\s*```").find(raw)?.groupValues?.get(1)
        if (fenced != null) {
            runCatching {
                val element = json.parseToJsonElement(fenced)
                if (element is JsonObject) return parseJsonOutcome(element)
            }
        }

        // 3. Tag-based fallback (very lenient)
        val tagMatch = parseTagBased(raw)
        if (tagMatch != null) return tagMatch

        // 4. Fallback
        Timber.w("ResponseParser: cannot parse, treating as Reasoning")
        return LlmOutcome.Reasoning(raw.take(2000))
    }

    private fun parseJsonOutcome(obj: JsonObject): LlmOutcome {
        val thought = obj["thought"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val next = obj["next"]?.jsonPrimitive?.contentOrNull?.lowercase()
        val emotion = obj["emotion"]?.jsonPrimitive?.contentOrNull

        return when (next) {
            "done" -> {
                val summary = obj["summary"]?.jsonPrimitive?.contentOrNull
                    ?: thought.ifBlank { "Task complete" }
                LlmOutcome.Done(summary, emotion)
            }
            "await_user" -> {
                val question = obj["question"]?.jsonPrimitive?.contentOrNull
                    ?: "Aku butuh info tambahan."
                LlmOutcome.AwaitUser(question, emotion)
            }
            "escalate" -> {
                val target = obj["target"]?.jsonPrimitive?.contentOrNull?.uppercase()
                    ?: "GEMINI"
                val reason = obj["reason"]?.jsonPrimitive?.contentOrNull ?: thought
                LlmOutcome.Escalate(reason, target)
            }
            else -> {
                // "continue" atau null
                val toolCallsArray = obj["tool_calls"]?.jsonArray
                if (toolCallsArray != null && toolCallsArray.isNotEmpty()) {
                    val calls = parseToolCalls(toolCallsArray)
                    LlmOutcome.ToolCalls(calls, thought, emotion)
                } else {
                    LlmOutcome.Reasoning(thought.ifBlank { "(no thought)" })
                }
            }
        }
    }

    private fun parseToolCalls(arr: JsonArray): List<ToolCall> {
        return arr.mapNotNull { element ->
            if (element !is JsonObject) return@mapNotNull null
            val toolName = element["tool"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val argsElement = element["args"] as? JsonObject ?: JsonObject(emptyMap())
            ToolCall(
                callId = java.util.UUID.randomUUID().toString(),
                tool = toolName,
                args = argsElement,
            )
        }
    }

    private fun parseTagBased(raw: String): LlmOutcome? {
        val nextMatch = Regex("(?i)NEXT:\\s*(\\w+)").find(raw) ?: return null
        val next = nextMatch.groupValues[1].lowercase()
        val thought = Regex("(?i)THOUGHT:\\s*(.+?)(?=\\n\\s*(?:TOOL_CALLS|NEXT|SUMMARY|QUESTION)|\\Z)", RegexOption.DOT_MATCHES_ALL)
            .find(raw)?.groupValues?.get(1)?.trim().orEmpty()

        return when (next) {
            "done" -> {
                val summary = Regex("(?i)SUMMARY:\\s*(.+)", RegexOption.DOT_MATCHES_ALL)
                    .find(raw)?.groupValues?.get(1)?.trim() ?: thought
                LlmOutcome.Done(summary, null)
            }
            "await_user" -> {
                val q = Regex("(?i)QUESTION:\\s*(.+)", RegexOption.DOT_MATCHES_ALL)
                    .find(raw)?.groupValues?.get(1)?.trim() ?: "?"
                LlmOutcome.AwaitUser(q, null)
            }
            else -> null
        }
    }
}

/**
 * Outcome dari LLM call. AgentRuntime branch berdasarkan ini.
 */
sealed class LlmOutcome {
    abstract val emotionTag: String?

    data class Done(val summary: String, override val emotionTag: String?) : LlmOutcome()
    data class AwaitUser(val question: String, override val emotionTag: String?) : LlmOutcome()
    data class ToolCalls(
        val calls: List<ToolCall>,
        val reasoning: String,
        override val emotionTag: String?,
    ) : LlmOutcome()
    data class Reasoning(val reasoning: String) : LlmOutcome() {
        override val emotionTag: String? = null
    }
    data class Escalate(val reason: String, val target: String) : LlmOutcome() {
        override val emotionTag: String? = null
    }
}

/**
 * Convenience JsonElement extension untuk extract field optional.
 */
fun JsonElement.asString(): String? = (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
