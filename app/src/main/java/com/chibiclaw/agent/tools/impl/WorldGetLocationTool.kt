package com.chibiclaw.agent.tools.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * world_get_location — current location via FusedLocationProvider.
 *
 * HIGH-severity context (PII), tapi MEDIUM dari sisi safety karena Lendra
 * device-pemilik. Confirmation overlay tetap minta sekali per session
 * (Phase 6 pre-auth via standing instruction kalau task domain == map/eta).
 */
class WorldGetLocationTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {

    override val spec = ToolSpec(
        name = "world_get_location",
        description = """
            Ambil koordinat GPS saat ini (lat/lng + accuracy meter).
            Pakai untuk ETA, navigation, location-aware reminder.
            Latency ~1-3s (cold cache), <500ms warm.
        """.trimIndent(),
        parameters = mapOf(
            "priority" to "enum: HIGH_ACCURACY | BALANCED | LOW_POWER (default BALANCED)",
        ),
        capability = ToolCapability(
            latencyMsRange = 400..3000,
            worksOn = listOf("FusedLocationProvider"),
            knownFail = listOf("GPS off / location services disabled"),
            requiresPermission = listOf("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(
            severity = ToolSeverity.MEDIUM,
            reason = "Lokasi presisi adalah PII — jangan transit cloud tanpa explicit consent.",
            preAuthorizable = true,
        ),
    )

    override fun isAvailable(): Boolean {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return granted
    }

    @Suppress("MissingPermission") // checked in isAvailable
    override suspend fun execute(call: ToolCall): ToolResult {
        if (!isAvailable()) return ToolResult.Error(
            call.callId,
            ErrorClass.PERMISSION_DENIED,
            "Permission ACCESS_FINE_LOCATION / COARSE belum granted",
            recoveryHint = "Buka Settings → Apps → ChibiClaw → Permissions → Location",
        )

        val client = LocationServices.getFusedLocationProviderClient(context)
        val token = CancellationTokenSource()
        val location = suspendCancellableCoroutine<android.location.Location?> { cont ->
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token)
                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                .addOnFailureListener { _ -> if (cont.isActive) cont.resume(null) }
            cont.invokeOnCancellation { token.cancel() }
        }
        if (location == null) {
            return ToolResult.Error(
                call.callId,
                ErrorClass.NOT_AVAILABLE,
                "Fused provider tidak return location — GPS mungkin off",
                recoveryHint = "Nyalakan Location di quick settings",
            )
        }
        return ToolResult.Success(
            callId = call.callId,
            data = JsonObject(mapOf(
                "lat" to JsonPrimitive(location.latitude),
                "lng" to JsonPrimitive(location.longitude),
                "accuracy_m" to JsonPrimitive(location.accuracy),
                "ts_ms" to JsonPrimitive(location.time),
                "provider" to JsonPrimitive(location.provider ?: "fused"),
            )),
        )
    }
}
