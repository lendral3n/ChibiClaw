package com.chibiclaw.agent.tools.impl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
 * world_get_installed_apps — list app yang launchable di home (filter LAUNCHER
 * category supaya tidak banjir dengan service-only package).
 *
 * Memerlukan QUERY_ALL_PACKAGES (sudah declared di manifest).
 */
class WorldGetInstalledAppsTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {

    override val spec = ToolSpec(
        name = "world_get_installed_apps",
        description = """
            List app yang punya launcher icon di device. Tiap entry punya
            package + label. Pakai sebelum panggil intent_open kalau LLM ragu
            package name persis.
        """.trimIndent(),
        parameters = mapOf(
            "filter" to "string (optional, substring match label or package)",
        ),
        capability = ToolCapability(
            latencyMsRange = 100..400,
            worksOn = listOf("system_package_manager"),
            requiresPermission = listOf("QUERY_ALL_PACKAGES"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(
            severity = ToolSeverity.LOW,
            reason = "Public list — no PII",
        ),
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        val filter = call.args["filter"]?.jsonPrimitive?.content?.lowercase()
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = pm.queryIntentActivities(intent, 0)
        val entries = resolved.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            val label = info.loadLabel(pm).toString()
            if (filter != null && !label.lowercase().contains(filter) && !pkg.lowercase().contains(filter)) {
                null
            } else pkg to label
        }.distinctBy { it.first }

        val array = buildJsonArray {
            entries.take(MAX_RESULTS).forEach { (pkg, label) ->
                add(buildJsonObject {
                    put("package", pkg)
                    put("label", label)
                })
            }
        }
        return ToolResult.Success(
            callId = call.callId,
            data = JsonObject(mapOf(
                "count" to JsonPrimitive(entries.size),
                "apps" to array,
                "truncated" to JsonPrimitive(entries.size > MAX_RESULTS),
            )),
        )
    }

    companion object {
        private const val MAX_RESULTS = 100
    }
}
