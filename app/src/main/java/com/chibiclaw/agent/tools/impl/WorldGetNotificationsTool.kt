package com.chibiclaw.agent.tools.impl

import com.chibiclaw.accessibility.ChibiNotificationListener
import com.chibiclaw.agent.tools.ErrorClass
import com.chibiclaw.agent.tools.Tool
import com.chibiclaw.agent.tools.ToolCall
import com.chibiclaw.agent.tools.ToolCapability
import com.chibiclaw.agent.tools.ToolCost
import com.chibiclaw.agent.tools.ToolResult
import com.chibiclaw.agent.tools.ToolSafety
import com.chibiclaw.agent.tools.ToolSeverity
import com.chibiclaw.agent.tools.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

class WorldGetNotificationsTool @Inject constructor() : Tool {

    override val spec = ToolSpec(
        name = "world_get_notifications",
        description = "List active notifications di status bar (snapshot saat ini).",
        parameters = mapOf(
            "limit" to "int (optional, default 10)",
            "package_filter" to "string (optional, filter by app package)",
        ),
        capability = ToolCapability(
            latencyMsRange = 50..200,
            worksOn = listOf("notification_listener_service"),
            requiresPermission = listOf("NOTIFICATION_LISTENER"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(
            severity = ToolSeverity.MEDIUM,
            reason = "Notification content bisa sensitif (WA pesan, OTP banking)",
        ),
    )

    override fun isAvailable(): Boolean = ChibiNotificationListener.isConnected()

    override suspend fun execute(call: ToolCall): ToolResult {
        val listener = ChibiNotificationListener.instance() ?: return ToolResult.Error(
            callId = call.callId,
            errorClass = ErrorClass.PERMISSION_DENIED,
            message = "Notification Listener belum aktif",
            recoveryHint = "Minta user enable di Settings → Notifications → Notification access → ChibiClaw",
        )
        val limit = call.args["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 10
        val pkgFilter = call.args["package_filter"]?.jsonPrimitive?.content

        val notifs = listener.snapshot()
            .let { if (pkgFilter != null) it.filter { n -> n.packageName == pkgFilter } else it }
            .sortedByDescending { it.postedAt }
            .take(limit)

        val array = buildJsonArray {
            notifs.forEach { n ->
                add(buildJsonObject {
                    put("package", n.packageName)
                    put("title", n.title)
                    put("text", n.text.take(200))
                    put("posted_at", n.postedAt)
                })
            }
        }
        return ToolResult.Success(
            callId = call.callId,
            data = JsonObject(mapOf(
                "count" to JsonPrimitive(notifs.size),
                "notifications" to array,
            )),
        )
    }
}
