package com.chibiclaw.ai.llm

import com.chibiclaw.agent.tools.ToolSpec
import com.chibiclaw.data.database.TaskChannel
import kotlinx.serialization.Serializable

/**
 * Input bundle ke InferenceAdapter. Self-contained: semua context yang LLM butuh.
 *
 * Phase 1: text-only. Phase 5 akan extend untuk multimodal (image input).
 */
data class AgentPrompt(
    val systemPrompt: String,
    val taskGoal: String,
    val taskChannel: TaskChannel,
    val taskHistory: List<String>,
    val recentTasks: List<String>,
    val worldSnapshot: String,
    val relevantMemory: List<String>,
    val emotionSignal: String? = null,
    val toolCatalog: List<ToolSpec>,
    val personaTraits: String,
    val iteration: Int,
    val maxIteration: Int,
    val responseFormat: ResponseFormat = ResponseFormat.JSON_STRUCTURED,
    /** Phase 9: optional inline image payload (JPEG bytes) untuk cloud vision fallback. */
    val imageJpegBytes: ByteArray? = null,
)

enum class ResponseFormat {
    JSON_STRUCTURED,        // strict JSON output
    JSON_IN_MARKDOWN,       // JSON within ```json``` fence
    TAG_BASED,              // THOUGHT/TOOL_CALLS/NEXT/SUMMARY tags
}

/**
 * Output dari InferenceAdapter. Raw text yang akan di-parse oleh ResponseParser.
 */
sealed class InferenceResult {
    data class Success(
        val raw: String,
        val tokensUsed: Int,
        val latencyMs: Long,
    ) : InferenceResult()

    data class Error(
        val errorClass: AdapterErrorClass,
        val message: String,
    ) : InferenceResult()
}

enum class AdapterErrorClass {
    AUTH_EXPIRED,
    RATE_LIMITED,
    NETWORK,
    TIMEOUT,
    CONTEXT_OVERFLOW,
    MODEL_NOT_LOADED,
    MODEL_ERROR,
    UNKNOWN,
}

@Serializable
data class InferenceChunk(
    val text: String,
    val isLast: Boolean,
)
