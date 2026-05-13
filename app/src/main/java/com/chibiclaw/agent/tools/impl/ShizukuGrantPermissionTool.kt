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

class ShizukuGrantPermissionTool @Inject constructor(
    private val shizukuManager: ShizukuManager,
) : Tool {

    override val spec = ToolSpec(
        name = "shizuku_grant_permission",
        description = """
            Grant runtime permission ke app target via `pm grant <pkg> <perm>`.
            Pakai untuk app yang butuh permission tapi user belum kasih via dialog.
            Permission harus declared di manifest app target.
        """.trimIndent(),
        parameters = mapOf(
            "package" to "string",
            "permission" to "string (e.g. 'android.permission.CAMERA')",
        ),
        capability = ToolCapability(
            latencyMsRange = 100..400,
            worksOn = listOf("any_app_with_manifest_perm"),
            requiresPermission = listOf("SHIZUKU_RUNNING"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(
            severity = ToolSeverity.HIGH,
            reason = "Granting runtime permission ke app lain — affect privacy + access",
            confirmationPromptTemplate = "Grant %2\$s ke %1\$s ?",
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
        val perm = call.args["permission"]?.jsonPrimitive?.content
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.INVALID_ARGS,
                message = "permission arg required",
            )
        val service = shizukuManager.acquireService() ?: return ToolResult.Error(
            callId = call.callId,
            errorClass = ErrorClass.PERMISSION_DENIED,
            message = "Shizuku tidak available",
        )
        val output = runCatching { service.exec("pm grant $pkg $perm", 3000) }
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
                "granted_to" to JsonPrimitive(pkg),
                "permission" to JsonPrimitive(perm),
                "output" to JsonPrimitive(output.take(500)),
            )),
        )
    }
}
