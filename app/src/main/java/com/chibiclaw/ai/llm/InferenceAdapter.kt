package com.chibiclaw.ai.llm

import kotlinx.coroutines.flow.Flow

/**
 * Abstraksi LLM backend. Phase 1: Gemma local + Stub (echo-based untuk dev).
 * Phase 4: Gemini free, Claude web reverse, GPT web reverse.
 *
 * Adapter HARUS thread-safe untuk single inference call.
 */
interface InferenceAdapter {

    val id: String
    val capability: AdapterCapability

    /** Lazy initialization (load model, validate session, dll). */
    suspend fun isAvailable(): Boolean

    /** Synchronous complete. Return raw text response (akan di-parse upstream). */
    suspend fun complete(prompt: AgentPrompt): InferenceResult

    /** Streaming token output (Phase 1+). */
    fun stream(prompt: AgentPrompt): Flow<InferenceChunk>

    /** Release resources (called on service stop). */
    suspend fun shutdown()
}

data class AdapterCapability(
    val displayName: String,
    val contextWindow: Int,
    val supportsToolCalling: Boolean,
    val supportsStreaming: Boolean,
    val supportsVision: Boolean,
    val supportsConstrainedDecoding: Boolean,
    val isLocal: Boolean,
    val estimatedTpsDecode: Float,
    val estimatedTpsPrefill: Float,
    val requiresAuth: Boolean,
)

enum class AdapterTarget {
    GEMMA, GEMINI, CLAUDE, GPT, STUB,
}
