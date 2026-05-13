package com.chibiclaw.agent.tools.impl

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
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
 * system_action — action sistem yang tidak butuh app target (flashlight, volume).
 *
 * Phase 1: FLASHLIGHT_ON/OFF + VOLUME_UP/DOWN.
 * Phase 3 akan extend dengan WIFI/BT toggle, brightness, DND, dll (butuh
 * WRITE_SETTINGS permission yang kompleks setup).
 */
class SystemActionTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {

    override val spec = ToolSpec(
        name = "system_action",
        description = "Trigger system action: flashlight, volume. Phase 1 limited; Phase 3 akan extend.",
        parameters = mapOf(
            "action" to "enum: FLASHLIGHT_ON, FLASHLIGHT_OFF, VOLUME_UP, VOLUME_DOWN",
        ),
        capability = ToolCapability(
            latencyMsRange = 50..300,
            worksOn = listOf("system_api"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(severity = ToolSeverity.LOW),
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        val action = call.args["action"]?.jsonPrimitive?.content?.uppercase()
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.INVALID_ARGS,
                message = "action arg required",
            )

        return runCatching {
            when (action) {
                "FLASHLIGHT_ON" -> toggleFlashlight(true)
                "FLASHLIGHT_OFF" -> toggleFlashlight(false)
                "VOLUME_UP" -> adjustVolume(AudioManager.ADJUST_RAISE)
                "VOLUME_DOWN" -> adjustVolume(AudioManager.ADJUST_LOWER)
                else -> ToolResult.Error(
                    callId = call.callId,
                    errorClass = ErrorClass.INVALID_ARGS,
                    message = "Unknown action: $action",
                    recoveryHint = "Pakai action dari list: FLASHLIGHT_ON, FLASHLIGHT_OFF, VOLUME_UP, VOLUME_DOWN",
                )
            }
        }.getOrElse { t ->
            Timber.e(t, "system_action exception")
            ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.UNKNOWN,
                message = t.message ?: t.javaClass.simpleName,
            )
        }
    }

    private fun toggleFlashlight(on: Boolean): ToolResult {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cm.cameraIdList.firstOrNull { id ->
            val char = cm.getCameraCharacteristics(id)
            char.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return ToolResult.Error(
            callId = "",
            errorClass = ErrorClass.NOT_AVAILABLE,
            message = "No flash hardware",
        )
        cm.setTorchMode(cameraId, on)
        Timber.i("Flashlight ${if (on) "ON" else "OFF"}")
        return ToolResult.Success(
            callId = "",
            data = JsonObject(mapOf("flashlight" to JsonPrimitive(on))),
        )
    }

    private fun adjustVolume(direction: Int): ToolResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustVolume(direction, AudioManager.FLAG_SHOW_UI)
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return ToolResult.Success(
            callId = "",
            data = JsonObject(mapOf(
                "volume" to JsonPrimitive(current),
                "max" to JsonPrimitive(max),
            )),
        )
    }
}
