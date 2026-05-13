package com.chibiclaw.ai.llm.adapters

import com.chibiclaw.ai.llm.AdapterCapability
import com.chibiclaw.ai.llm.AgentPrompt
import com.chibiclaw.ai.llm.InferenceAdapter
import com.chibiclaw.ai.llm.InferenceChunk
import com.chibiclaw.ai.llm.InferenceResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * StubAdapter — dummy LLM untuk dev tanpa load model real.
 *
 * Strategi: parse task goal, emit ToolCall paling masuk akal berdasarkan keyword
 * sederhana. **Bukan policy code yang bersaing dengan LLM** — hanya placeholder
 * supaya agent loop bisa di-test end-to-end sambil menunggu Gemma di-load di
 * device.
 *
 * Akan diganti oleh GemmaAdapter saat model siap.
 */
@Singleton
class StubAdapter @Inject constructor() : InferenceAdapter {

    override val id = "stub"
    override val capability = AdapterCapability(
        displayName = "Stub (Dev Placeholder)",
        contextWindow = 4096,
        supportsToolCalling = true,
        supportsStreaming = false,
        supportsVision = false,
        supportsConstrainedDecoding = false,
        isLocal = true,
        estimatedTpsDecode = 1000f,
        estimatedTpsPrefill = 10_000f,
        requiresAuth = false,
    )

    override suspend fun isAvailable(): Boolean = true

    override suspend fun complete(prompt: AgentPrompt): InferenceResult {
        val goal = prompt.taskGoal.lowercase()
        val historyLen = prompt.taskHistory.size

        // Sangat simple rule-based — placeholder. Real Gemma akan replace.
        val response = when {
            historyLen > 0 -> doneResponse("Task selesai (stub mode)")
            "senter" in goal || "flashlight" in goal -> toolCallResponse(
                thought = "User minta senter. Pakai system_action FLASHLIGHT_ON.",
                tool = "system_action",
                args = mapOf("action" to "FLASHLIGHT_ON"),
            )
            "buka " in goal -> {
                val target = goal.substringAfter("buka ").substringBefore(" ").trim()
                toolCallResponse(
                    thought = "User minta buka $target. Pakai intent_open.",
                    tool = "intent_open",
                    args = mapOf("query" to target),
                )
            }
            "ingat" in goal || "remember" in goal -> toolCallResponse(
                thought = "User mau save info. Pakai memory_remember.",
                tool = "memory_remember",
                args = mapOf(
                    "category" to "FACT",
                    "key" to "stub.echo",
                    "value" to goal,
                ),
            )
            "halo" in goal || "hai" in goal -> doneResponse(
                "Halo Lendra~ Aku Fuu. Mode stub aktif, Gemma belum loaded.",
                emotion = "joy",
            )
            else -> awaitUserResponse(
                "Aku belum bisa parse task ini di stub mode. Gemma 4 akan handle proper di Phase 1 final."
            )
        }

        return InferenceResult.Success(
            raw = response,
            tokensUsed = response.length / 4,
            latencyMs = 100,
        )
    }

    override fun stream(prompt: AgentPrompt): Flow<InferenceChunk> = flowOf(
        InferenceChunk(text = "", isLast = true)
    )

    override suspend fun shutdown() {
        // no-op
    }

    private fun toolCallResponse(
        thought: String,
        tool: String,
        args: Map<String, String>,
    ): String {
        val argsJson = args.entries.joinToString(", ") { (k, v) -> "\"$k\": \"$v\"" }
        return """
            {
              "thought": "$thought",
              "tool_calls": [{"tool": "$tool", "args": {$argsJson}}],
              "next": "continue",
              "summary": null,
              "question": null,
              "emotion": "neutral"
            }
        """.trimIndent()
    }

    private fun doneResponse(summary: String, emotion: String = "satisfied"): String = """
        {
          "thought": "Task complete, emit done.",
          "tool_calls": [],
          "next": "done",
          "summary": "$summary",
          "question": null,
          "emotion": "$emotion"
        }
    """.trimIndent()

    private fun awaitUserResponse(question: String): String = """
        {
          "thought": "Butuh clarification dari user.",
          "tool_calls": [],
          "next": "await_user",
          "summary": null,
          "question": "$question",
          "emotion": "uncertain"
        }
    """.trimIndent()
}
