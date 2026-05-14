package com.chibiclaw.agent.tools.impl

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.chibiclaw.agent.tools.ErrorClass
import com.chibiclaw.agent.tools.Tool
import com.chibiclaw.agent.tools.ToolCall
import com.chibiclaw.agent.tools.ToolCapability
import com.chibiclaw.agent.tools.ToolCost
import com.chibiclaw.agent.tools.ToolResult
import com.chibiclaw.agent.tools.ToolSafety
import com.chibiclaw.agent.tools.ToolSeverity
import com.chibiclaw.agent.tools.ToolSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * world_get_schedule — query CalendarContract Events untuk window waktu tertentu.
 *
 * Pakai untuk "what's on my schedule today", auto-summary morning brief, dll.
 *
 * Args:
 *  - from_ms (optional, default now)
 *  - to_ms (optional, default now + 24h)
 *  - max (optional, default 20)
 */
class WorldGetScheduleTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {

    override val spec = ToolSpec(
        name = "world_get_schedule",
        description = """
            Query event kalender dalam window waktu (default 24 jam dari sekarang).
            Return: title, start_ms, end_ms, location, all_day flag.
        """.trimIndent(),
        parameters = mapOf(
            "from_ms" to "long (optional, default now)",
            "to_ms" to "long (optional, default now+24h)",
            "max" to "int (optional, default 20)",
        ),
        capability = ToolCapability(
            latencyMsRange = 80..400,
            worksOn = listOf("CalendarProvider"),
            requiresPermission = listOf("READ_CALENDAR"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(
            severity = ToolSeverity.MEDIUM,
            reason = "Event personal — jangan transit cloud tanpa eksplisit.",
            preAuthorizable = true,
        ),
    )

    override fun isAvailable(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun execute(call: ToolCall): ToolResult {
        if (!isAvailable()) return ToolResult.Error(
            call.callId,
            ErrorClass.PERMISSION_DENIED,
            "Permission READ_CALENDAR belum granted",
            recoveryHint = "Settings → Apps → ChibiClaw → Permissions → Calendar",
        )

        val now = System.currentTimeMillis()
        val from = call.args["from_ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: now
        val to = call.args["to_ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: (now + 24L * 3600 * 1000)
        val limit = call.args["max"]?.jsonPrimitive?.content?.toIntOrNull() ?: 20

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, from)
        ContentUris.appendId(builder, to)
        val uri = builder.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
        )

        val events = mutableListOf<Map<String, Any?>>()
        runCatching {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext() && events.size < limit) {
                    events += mapOf(
                        "title" to (cursor.getString(1) ?: "(no title)"),
                        "start_ms" to cursor.getLong(2),
                        "end_ms" to cursor.getLong(3),
                        "location" to cursor.getString(4).orEmpty(),
                        "all_day" to (cursor.getInt(5) == 1),
                    )
                }
            }
        }.onFailure {
            return ToolResult.Error(
                call.callId,
                ErrorClass.UNKNOWN,
                "Calendar query exception: ${it.message?.take(120)}",
            )
        }

        val array = buildJsonArray {
            events.forEach { e ->
                add(buildJsonObject {
                    put("title", e["title"] as String)
                    put("start_ms", e["start_ms"] as Long)
                    put("end_ms", e["end_ms"] as Long)
                    put("location", e["location"] as String)
                    put("all_day", e["all_day"] as Boolean)
                })
            }
        }
        return ToolResult.Success(
            callId = call.callId,
            data = JsonObject(mapOf(
                "count" to JsonPrimitive(events.size),
                "from_ms" to JsonPrimitive(from),
                "to_ms" to JsonPrimitive(to),
                "events" to array,
            )),
        )
    }
}
