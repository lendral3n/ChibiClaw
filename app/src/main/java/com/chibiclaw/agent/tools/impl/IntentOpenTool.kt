package com.chibiclaw.agent.tools.impl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject

/**
 * intent_open — launch app lewat Intent.
 *
 * Args:
 *   - package: string (e.g. "com.spotify.music")
 *   - OR query: string (LLM kirim nama app, kita resolve via PackageManager match)
 */
class IntentOpenTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {

    override val spec = ToolSpec(
        name = "intent_open",
        description = "Buka app Android lewat Intent. Cepat (~200ms), works hampir semua app. Hanya buka, tidak interact UI dalam.",
        parameters = mapOf(
            "package" to "string (optional, exact package name)",
            "query" to "string (optional, nama app untuk di-resolve)",
        ),
        capability = ToolCapability(
            latencyMsRange = 100..600,
            worksOn = listOf("most_apps"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(severity = ToolSeverity.LOW),
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        val pkg = call.args["package"]?.jsonPrimitive?.content
        val query = call.args["query"]?.jsonPrimitive?.content
        val resolved = pkg ?: resolveByQuery(query)
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.AMBIGUOUS,
                message = "Tidak bisa resolve package dari args (package atau query wajib)",
                recoveryHint = "Pakai world_get_installed_apps untuk list apps available",
            )

        val launchIntent = context.packageManager.getLaunchIntentForPackage(resolved)
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.SELECTOR_NOT_FOUND,
                message = "Package '$resolved' tidak ditemukan atau tidak punya launcher activity",
            )

        return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            Timber.i("intent_open launched: $resolved")
            ToolResult.Success(
                callId = call.callId,
                data = JsonObject(mapOf("launched_package" to JsonPrimitive(resolved))),
            )
        } catch (t: Throwable) {
            ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.UNKNOWN,
                message = "Launch failed: ${t.message}",
            )
        }
    }

    private fun resolveByQuery(query: String?): String? {
        if (query.isNullOrBlank()) return null
        val needle = query.trim().lowercase()
        val pm = context.packageManager
        // Direct package match
        runCatching { pm.getApplicationInfo(needle, 0) }.onSuccess { return it.packageName }
        // Match by label
        val flags = PackageManager.GET_META_DATA
        val installed = try {
            pm.getInstalledApplications(flags)
        } catch (t: Throwable) {
            emptyList()
        }
        return installed.firstOrNull { app ->
            val label = pm.getApplicationLabel(app).toString().lowercase()
            label.contains(needle) || app.packageName.contains(needle, ignoreCase = true)
        }?.packageName
    }
}
