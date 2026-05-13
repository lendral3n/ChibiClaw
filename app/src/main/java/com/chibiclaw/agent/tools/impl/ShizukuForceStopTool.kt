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

class ShizukuForceStopTool @Inject constructor(
    private val shizukuManager: ShizukuManager,
) : Tool {

    override val spec = ToolSpec(
        name = "shizuku_force_stop",
        description = """
            Force-stop app via `am force-stop <package>`.
            User task di app target akan ter-interrupt (unsaved state hilang).
        """.trimIndent(),
        parameters = mapOf("package" to "string (e.g. 'com.spotify.music')"),
        capability = ToolCapability(
            latencyMsRange = 100..400,
            worksOn = listOf("any_app"),
            requiresPermission = listOf("SHIZUKU_RUNNING"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(
            severity = ToolSeverity.HIGH,
            reason = "Force-stop app — task user di app target interrupted",
            confirmationPromptTemplate = "Force-stop %1\$s ?",
            preAuthorizable = true,
        ),
    )

    override fun isAvailable(): Boolean = shizukuManager.isShizukuAvailable() && shizukuManager.hasPermission()

    override suspend fun execute(call: ToolCall): ToolResult {
        val pkg = call.args["package"]?.jsonPrimitive?.content
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.INVALID_ARGS,
                message = "package arg required",
            )
        val service = shizukuManager.acquireService() ?: return ToolResult.Error(
            callId = call.callId,
            errorClass = ErrorClass.PERMISSION_DENIED,
            message = "Shizuku tidak available",
        )
        val output = runCatching { service.exec("am force-stop $pkg", 3000) }
            .getOrElse { t ->
                return ToolResult.Error(
                    callId = call.callId,
                    errorClass = ErrorClass.UNKNOWN,
                    message = t.message ?: "exec failed",
                )
            }
        return ToolResult.Success(
            callId = call.callId,
            data = JsonObject(mapOf(
                "stopped" to JsonPrimitive(pkg),
                "output" to JsonPrimitive(output.take(500)),
            )),
        )
    }
}
