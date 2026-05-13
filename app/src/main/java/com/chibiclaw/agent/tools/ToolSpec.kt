package com.chibiclaw.agent.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Tool descriptor — metadata yang LLM baca + Dispatcher gunakan untuk routing.
 *
 * Lihat docs/architecture/13-tool-catalog.md untuk full spec semua tools.
 */
data class ToolSpec(
    val name: String,                      // unique identifier
    val description: String,               // free-form description untuk LLM
    val parameters: Map<String, String>,   // param name → type description ("string", "int", "enum: X | Y")
    val capability: ToolCapability,
    val safety: ToolSafety,
)

data class ToolCapability(
    val latencyMsRange: IntRange,
    val worksOn: List<String>,              // "most_apps", "accessibility_exposed_apps", dll
    val knownFail: List<String> = emptyList(),
    val requiresPermission: List<String> = emptyList(),
    val cost: ToolCost = ToolCost.LOW,
)

data class ToolSafety(
    val severity: ToolSeverity,
    val reason: String? = null,
    val confirmationPromptTemplate: String? = null,
    val preAuthorizable: Boolean = true,
)

enum class ToolSeverity { LOW, MEDIUM, HIGH }
enum class ToolCost { LOW, MEDIUM, HIGH }

/**
 * Tool invocation dari LLM.
 */
@Serializable
data class ToolCall(
    val callId: String,
    val tool: String,
    val args: JsonObject,
)

/**
 * Hasil eksekusi tool. LLM observe di iterasi berikutnya.
 */
@Serializable
sealed class ToolResult {
    abstract val callId: String

    @Serializable
    @kotlinx.serialization.SerialName("success")
    data class Success(
        override val callId: String,
        val data: JsonObject = JsonObject(emptyMap()),
    ) : ToolResult()

    @Serializable
    @kotlinx.serialization.SerialName("error")
    data class Error(
        override val callId: String,
        val errorClass: ErrorClass,
        val message: String,
        val fatal: Boolean = false,
        val recoveryHint: String? = null,
    ) : ToolResult()

    @Serializable
    @kotlinx.serialization.SerialName("timeout")
    data class Timeout(
        override val callId: String,
        val elapsedMs: Long,
    ) : ToolResult()

    @Serializable
    @kotlinx.serialization.SerialName("user_denied")
    data class UserDenied(override val callId: String) : ToolResult()
}

enum class ErrorClass {
    SELECTOR_NOT_FOUND,
    PERMISSION_DENIED,
    TIMEOUT,
    AMBIGUOUS,
    NETWORK_ERROR,
    RATE_LIMITED,
    INVALID_ARGS,
    NOT_AVAILABLE,
    UNKNOWN,
}

/**
 * Tool implementation. Stateless — state simpan di Task atau Memory.
 */
interface Tool {
    val spec: ToolSpec
    fun isAvailable(): Boolean = true
    suspend fun execute(call: ToolCall): ToolResult
}
