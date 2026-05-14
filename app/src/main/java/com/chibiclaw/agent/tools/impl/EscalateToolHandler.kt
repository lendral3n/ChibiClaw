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
import com.chibiclaw.ai.llm.AdapterTarget
import com.chibiclaw.ai.llm.InferenceRouter
import com.chibiclaw.compliance.AuditLogger
import com.chibiclaw.data.database.AuditActionType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject

/**
 * Tool `escalate_to_cloud` — LLM emit kalau task butuh adapter cloud (Gemini /
 * Claude / GPT). Tool TIDAK melakukan cloud call; cuma pin adapter via
 * InferenceRouter. Iterasi berikutnya AgentRuntime call adapter pinned.
 *
 * Severity MEDIUM karena data ringkasan task akan transit cloud — SafetyGate
 * akan minta konfirmasi user kecuali sudah pre-authorize.
 *
 * Catatan: callId tetap MEDIUM bukan HIGH supaya overlay tidak nge-block tiap
 * task; tapi reason+confirmationPromptTemplate sudah explicit transit cloud.
 * Kalau Lendra mau full opt-in confirm, tinggal naikkan severity di sini.
 */
class EscalateToolHandler @Inject constructor(
    private val router: InferenceRouter,
    private val auditLogger: AuditLogger,
) : Tool {

    override val spec = ToolSpec(
        name = "escalate_to_cloud",
        description = """
            Signal bahwa task butuh model LLM lebih kuat dari Gemma lokal.
            Pakai saat: reasoning multi-step kompleks, long-context analysis,
            atau task ambiguous yang Gemma struggle.
            Tidak melakukan cloud call sendiri — cuma pin adapter. Iterasi
            berikutnya AgentRuntime akan pakai adapter pinned.
        """.trimIndent(),
        parameters = mapOf(
            "reason" to "string (kenapa butuh escalate)",
            "target" to "enum: GEMINI | CLAUDE | GPT (default GEMINI)",
        ),
        capability = ToolCapability(
            latencyMsRange = 20..100,
            worksOn = listOf("router-pin"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(
            severity = ToolSeverity.MEDIUM,
            reason = "Task ringkasan akan transit cloud — verifikasi privasi.",
            confirmationPromptTemplate = "Kirim task ke %2\$s? (alasan: %1\$s)",
            preAuthorizable = true,
        ),
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        val targetStr = call.args["target"]?.jsonPrimitive?.content?.uppercase() ?: "GEMINI"
        val reason = call.args["reason"]?.jsonPrimitive?.content
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.INVALID_ARGS,
                message = "reason arg required",
            )
        val target = runCatching { AdapterTarget.valueOf(targetStr) }.getOrNull()
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.INVALID_ARGS,
                message = "target invalid: '$targetStr'. Pilih GEMINI | CLAUDE | GPT.",
            )

        // ToolDispatcher stamps `__taskId` ke args sebelum execute supaya
        // router bisa pin ke real task.id (bukan callId).
        val pinTaskId = call.args["__taskId"]?.jsonPrimitive?.content
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.INVALID_ARGS,
                message = "Internal: __taskId tidak ter-stamp oleh dispatcher.",
            )
        val adapter = router.escalate(target, pinTaskId)
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.NOT_AVAILABLE,
                message = "$target adapter belum siap (session/API key missing atau quota habis).",
                recoveryHint = "Setup wizard → Cloud → konfigurasi $target dulu.",
            )

        Timber.i("Escalate granted: target=$target reason='$reason' → ${adapter.id}")
        // Phase 9: audit log dedicated LLM_CALL_CLOUD (sebelumnya generik TOOL_EXECUTED).
        auditLogger.log(
            actionType = AuditActionType.LLM_CALL_CLOUD,
            dataSummary = "Escalate to ${adapter.id} (reason: ${reason.take(80)})",
            taskId = pinTaskId,
            toolName = "escalate_to_cloud",
            cloudDestination = adapter.id,
        )
        return ToolResult.Success(
            callId = call.callId,
            data = JsonObject(mapOf(
                "switched_to" to JsonPrimitive(adapter.id),
                "display_name" to JsonPrimitive(adapter.capability.displayName),
                "reason" to JsonPrimitive(reason),
            )),
        )
    }
}
