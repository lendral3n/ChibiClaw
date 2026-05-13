package com.chibiclaw.agent.tools

import com.chibiclaw.agent.tools.safety.SafetyGate
import com.chibiclaw.compliance.AuditLogger
import com.chibiclaw.data.database.AuditActionType
import com.chibiclaw.data.database.AuditResultStatus
import com.chibiclaw.data.database.TaskEntity
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatcher — execute ToolCall apa adanya. Tidak ada policy code di atas tools.
 *
 * Responsibilities:
 *  - Resolve tool dari registry
 *  - Timeout wrap (3x estimated latency)
 *  - Inline safety gate untuk HIGH severity via SafetyGate (Phase 3+):
 *    ConfirmationOverlay 30s auto-deny.
 *  - Audit log per execute (termasuk USER_DENIED outcome).
 */
@Singleton
class ToolDispatcher @Inject constructor(
    private val registry: ToolRegistry,
    private val auditLogger: AuditLogger,
    private val safetyGate: SafetyGate,
) {

    suspend fun execute(call: ToolCall, task: TaskEntity): ToolResult {
        val tool = registry.get(call.tool) ?: return ToolResult.Error(
            callId = call.callId,
            errorClass = ErrorClass.NOT_AVAILABLE,
            message = "Tool '${call.tool}' not registered",
            fatal = false,
            recoveryHint = "Pakai tool dari catalog yang available",
        )

        // HIGH severity → consult SafetyGate (overlay confirmation, auto-deny 30s).
        // Phase 6: pre-auth dari StandingInstruction bisa skip overlay.
        if (tool.spec.safety.severity == ToolSeverity.HIGH) {
            val approved = safetyGate.requestApproval(
                toolSpec = tool.spec,
                call = call,
                task = task,
            )
            if (!approved) {
                Timber.w("SafetyGate denied tool=${tool.spec.name} task=${task.id}")
                val denied = ToolResult.UserDenied(
                    callId = call.callId,
                    reason = "User menolak / timeout konfirmasi HIGH severity.",
                )
                auditLogger.log(
                    actionType = AuditActionType.TOOL_EXECUTED,
                    dataSummary = "Tool ${call.tool} DENIED via SafetyGate",
                    taskId = task.id,
                    toolName = call.tool,
                    resultStatus = AuditResultStatus.USER_DENIED,
                )
                return denied
            }
        }

        val timeoutMs = tool.spec.capability.latencyMsRange.last * 3L

        // Phase 4: stamp __taskId untuk tools yang butuh task context
        // (escalate_to_cloud pin per-task adapter). Tools lain ignore.
        val effectiveCall = if (call.tool == "escalate_to_cloud") {
            val argsWithTask = call.args.toMutableMap()
            argsWithTask["__taskId"] = JsonPrimitive(task.id)
            call.copy(args = JsonObject(argsWithTask))
        } else call

        val result = try {
            withTimeoutOrNull(timeoutMs) {
                tool.execute(effectiveCall)
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
