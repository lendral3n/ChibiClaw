package com.chibiclaw.agent.tools.impl

import android.graphics.Rect
import com.chibiclaw.agent.tools.ErrorClass
import com.chibiclaw.agent.tools.Tool
import com.chibiclaw.agent.tools.ToolCall
import com.chibiclaw.agent.tools.ToolCapability
import com.chibiclaw.agent.tools.ToolCost
import com.chibiclaw.agent.tools.ToolResult
import com.chibiclaw.agent.tools.ToolSafety
import com.chibiclaw.agent.tools.ToolSeverity
import com.chibiclaw.agent.tools.ToolSpec
import com.chibiclaw.vision.ocr.MlKitOcr
import com.chibiclaw.vision.screenshot.ImageProcessor
import com.chibiclaw.vision.screenshot.ScreenCapture
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * vision_extract_text — OCR via ML Kit Text Recognition v2.
 *
 * Args optional region (x,y,w,h px native). Kalau region absent, full screen.
 *
 * Latency target <500ms typical (ML Kit punya on-device model bundled di GMS).
 */
class VisionExtractTextTool @Inject constructor(
    private val screenCapture: ScreenCapture,
    private val processor: ImageProcessor,
    private val ocr: MlKitOcr,
) : Tool {

    override val spec = ToolSpec(
        name = "vision_extract_text",
        description = """
            Extract text dari layar via OCR (ML Kit). Hasil plain-text urut
            top-bottom. Bisa region-restricted via args x/y/w/h px.
            Faster dari vision_describe untuk text-heavy screen.
        """.trimIndent(),
        parameters = mapOf(
            "x" to "int (optional, region top-left x)",
            "y" to "int (optional, region top-left y)",
            "w" to "int (optional, region width)",
            "h" to "int (optional, region height)",
        ),
        capability = ToolCapability(
            latencyMsRange = 200..800,
            worksOn = listOf("any_app_screen with text"),
            requiresPermission = listOf("MediaProjection"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(
            severity = ToolSeverity.MEDIUM,
            reason = "OCR extract bisa kena password field — Phase 9 add keyboard-active detection.",
        ),
    )

    override fun isAvailable(): Boolean = screenCapture.isAvailable()

    override suspend fun execute(call: ToolCall): ToolResult {
        val raw = screenCapture.snapshotRaw() ?: return ToolResult.Error(
            call.callId,
            ErrorClass.NOT_AVAILABLE,
            "Capture screenshot gagal",
        )

        val region = readRegion(call)
        val target = if (region != null) {
            processor.crop(raw, region) ?: return ToolResult.Error(
                call.callId,
                ErrorClass.INVALID_ARGS,
                "Region invalid: $region",
            )
        } else raw

        val text = ocr.extractText(target)
        return ToolResult.Success(
            callId = call.callId,
            data = JsonObject(mapOf(
                "text" to JsonPrimitive(text),
                "char_count" to JsonPrimitive(text.length),
            )),
        )
    }

    private fun readRegion(call: ToolCall): Rect? {
        val x = call.args["x"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val y = call.args["y"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val w = call.args["w"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val h = call.args["h"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        return Rect(x, y, x + w, y + h)
    }
}
