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
import com.chibiclaw.permissions.ShizukuManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class ShizukuExecTool @Inject constructor(
    private val shizukuManager: ShizukuManager,
) : Tool {

    override val spec = ToolSpec(
        name = "shizuku_exec",
        description = """
            Execute ADB shell command via Shizuku UserService (ADB-level priv).
            Latency: ~50-200ms. Bisa: am, pm, dumpsys, input, settings, content, cmd.
            Limitations: tidak bisa root command (perlu Sui mod).
            Sebelum pakai, user wajib setup Shizuku (wireless ADB pairing atau root).
        """.trimIndent(),
        parameters = mapOf(
            "command" to "string (shell command, e.g. 'am force-stop com.spotify.music')",
            "timeout_ms" to "int (optional, default 5000)",
        ),
        capability = ToolCapability(
            latencyMsRange = 50..5_000,
            worksOn = listOf("any_app_via_adb_capability"),
            knownFail = listOf("root_only_commands (perlu Sui)"),
            requiresPermission = listOf("SHIZUKU_RUNNING"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(
            severity = ToolSeverity.HIGH,
            reason = "ADB-level command: bisa force-stop / uninstall / grant runtime permission ke app lain",
            confirmationPromptTemplate = "Jalankan shell command: %1\$s ?",
            preAuthorizable = true,
        ),
    )

    override fun isAvailable(): Boolean = shizukuManager.isShizukuAvailable() && shizukuManager.hasPermission()

    override suspend fun execute(call: ToolCall): ToolResult {
        val command = call.args["command"]?.jsonPrimitive?.content
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.INVALID_ARGS,
                message = "command arg required",
            )
        val timeoutMs = call.args["timeout_ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: 5000L

        val service = shizukuManager.acquireService() ?: return ToolResult.Error(
            callId = call.callId,
            errorClass = ErrorClass.PERMISSION_DENIED,
            message = "Shizuku tidak available atau ChibiClaw belum di-grant",
            recoveryHint = "Minta user buka app Shizuku, start service via ADB, lalu grant permission ChibiClaw",
        )
        val output = runCatching { service.exec(command, timeoutMs) }
            .getOrElse { t ->
                return ToolResult.Error(
                    callId = call.callId,
                    errorClass = ErrorClass.UNKNOWN,
                    message = "Shizuku exec exception: ${t.message}",
                )
            }
        val truncated = output.take(4000)
        return ToolResult.Success(
            callId = call.callId,
            data = JsonObject(mapOf(
                "stdout" to JsonPrimitive(truncated),
                "truncated" to JsonPrimitive(output.length > 4000),
            )),
        )
    }
}
