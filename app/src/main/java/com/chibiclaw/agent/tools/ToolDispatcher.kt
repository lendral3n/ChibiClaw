package com.chibiclaw.agent.tools

import com.chibiclaw.compliance.AuditLogger
import com.chibiclaw.data.database.AuditActionType
import com.chibiclaw.data.database.AuditResultStatus
import com.chibiclaw.data.database.TaskEntity
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatcher — execute ToolCall apa adanya. Tidak ada policy code di atas tools.
 *
 * Phase 1 responsibilities:
 *  - Resolve tool dari registry
 *  - Timeout wrap (3x estimated latency)
 *  - Inline safety gate untuk HIGH severity (Phase 1: simple skip kalau task channel STANDING/AUTONOMOUS dengan pre-auth; Phase 3+ tambah ConfirmationOverlay)
 *  - Audit log per execute
 */
@Singleton
class ToolDispatcher @Inject constructor(
    private val registry: ToolRegistry,
    private val auditLogger: AuditLogger,
) {

    suspend fun execute(call: ToolCall, task: TaskEntity): ToolResult {
        val tool = registry.get(call.tool) ?: return ToolResult.Error(
            callId = call.callId,
            errorClass = ErrorClass.NOT_AVAILABLE,
            message = "Tool '${call.tool}' not registered",
            fatal = false,
            recoveryHint = "Pakai tool dari catalog yang available",
        )

        // Phase 1 inline safety — kalau HIGH severity dan task channel CHAT,
        // butuh confirmation. Phase 1 simplification: log + skip kalau task
        // tidak ada pre-auth flag. Phase 3 akan add ConfirmationOverlay UI.
        if (tool.spec.safety.severity == ToolSeverity.HIGH) {
            // Phase 1: skip enforcement (tools HIGH severity belum di-implement di Phase 1).
            // Phase 3+: real confirmation overlay.
            Timber.d("HIGH severity tool: ${tool.spec.name} (Phase 1 — confirmation overlay TBD)")
        }

        val timeoutMs = tool.spec.capability.latencyMsRange.last * 3L

        val result = try {
            withTimeoutOrNull(timeoutMs) {
                tool.execute(call)
            } ?: ToolResult.Timeout(call.callId, timeoutMs)
        } catch (t: TimeoutCancellationException) {
            ToolResult.Timeout(call.callId, timeoutMs)
        } catch (t: Throwable) {
            Timber.e(t, "Tool ${call.tool} execute exception")
            ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.UNKNOWN,
                message = t.message ?: t.javaClass.simpleName,
                fatal = false,
            )
        }

        // Audit log
        val auditStatus = when (result) {
            is ToolResult.Success -> AuditResultStatus.SUCCESS
            is ToolResult.Error -> AuditResultStatus.FAILED
            is ToolResult.Timeout -> AuditResultStatus.TIMEOUT
            is ToolResult.UserDenied -> AuditResultStatus.USER_DENIED
        }
        auditLogger.log(
            actionType = AuditActionType.TOOL_EXECUTED,
            dataSummary = "Tool ${call.tool} args=${call.args} → ${result::class.simpleName}",
            taskId = task.id,
            toolName = call.tool,
            resultStatus = auditStatus,
        )

        return result
    }
}
