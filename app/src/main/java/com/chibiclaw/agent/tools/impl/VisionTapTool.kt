package com.chibiclaw.agent.tools.impl

import com.chibiclaw.accessibility.ChibiAccessibilityService
import com.chibiclaw.agent.tools.ErrorClass
import com.chibiclaw.agent.tools.Tool
import com.chibiclaw.agent.tools.ToolCall
import com.chibiclaw.agent.tools.ToolCapability
import com.chibiclaw.agent.tools.ToolCost
import com.chibiclaw.agent.tools.ToolResult
import com.chibiclaw.agent.tools.ToolSafety
import com.chibiclaw.agent.tools.ToolSeverity
import com.chibiclaw.agent.tools.ToolSpec
import com.chibiclaw.vision.llm.GroundingResult
import com.chibiclaw.vision.llm.MiniCPMVInference
import com.chibiclaw.vision.llm.VisionPromptBuilder
import com.chibiclaw.vision.screenshot.ScreenCapture
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject

/**
 * vision_tap — screenshot → MiniCPM-V grounding → tap koordinat via Accessibility.
 *
 * Fallback chain:
 *  - Kalau MiniCPM-V tidak tersedia (model belum di-push atau JNI absent),
 *    return ErrorClass.NOT_AVAILABLE dengan hint untuk escalate_to_cloud
 *    (Gemini Flash multimodal).
 *  - Kalau Accessibility tidak terhubung, return PERMISSION_DENIED.
 */
class VisionTapTool @Inject constructor(
    private val screenCapture: ScreenCapture,
    private val visionLlm: MiniCPMVInference,
) : Tool {

    override val spec = ToolSpec(
        name = "vision_tap",
        description = """
            Tap elemen UI by visual grounding — pakai screenshot + LLM vision.
            Fallback saat a11y_click gagal (SELECTOR_NOT_FOUND) di app yang
            obfuscate hierarchy (TikTok, IG, Shopee).
            Latensi: 2-4s. Akurasi varies per app.
        """.trimIndent(),
        parameters = mapOf(
            "query" to "string (deskripsi elemen, e.g. 'tombol kirim WA', 'search icon top right')",
        ),
        capability = ToolCapability(
            latencyMsRange = 1500..5000,
            worksOn = listOf("any_app_screen"),
            knownFail = listOf("app pakai overlay non-standar", "elemen out of viewport"),
            requiresPermission = listOf("MediaProjection", "Accessibility"),
            cost = ToolCost.MEDIUM,
        ),
        safety = ToolSafety(
            severity = ToolSeverity.MEDIUM,
            reason = "Screenshot diproses lokal — tidak persistent, tidak transit cloud",
        ),
    )

    private val json = Json { ignoreUnknownKeys = true }

    override fun isAvailable(): Boolean =
        screenCapture.isAvailable() && ChibiAccessibilityService.isConnected()

    override suspend fun execute(call: ToolCall): ToolResult {
        val query = call.args["query"]?.jsonPrimitive?.content
            ?: return ToolResult.Error(call.callId, ErrorClass.INVALID_ARGS, "query arg required")

        if (!screenCapture.isAvailable()) {
            return ToolResult.Error(
                call.callId,
                ErrorClass.PERMISSION_DENIED,
                "MediaProjection token belum tersedia",
                recoveryHint = "Setup wizard → Vision → grant MediaProjection",
            )
        }
        val a11y = ChibiAccessibilityService.instance() ?: return ToolResult.Error(
            call.callId,
            ErrorClass.PERMISSION_DENIED,
            "Accessibility belum terhubung — tidak bisa dispatch tap",
        )

        val bitmap = screenCapture.snapshotRaw() ?: return ToolResult.Error(
            call.callId,
            ErrorClass.NOT_AVAILABLE,
            "Capture screenshot gagal — token mungkin expired",
            recoveryHint = "Re-grant MediaProjection",
        )

        val prompt = VisionPromptBuilder.grounding(query, bitmap.width, bitmap.height)
        val raw = visionLlm.infer(prompt, bitmap)
            ?: return ToolResult.Error(
                call.callId,
                ErrorClass.NOT_AVAILABLE,
                "Vision LLM (MiniCPM-V) belum siap — model belum di-push atau JNI .so absent.",
                recoveryHint = "Push model files/models/minicpm-v-4-6-q4.gguf, atau emit escalate_to_cloud target=GEMINI untuk vision di cloud.",
            )

        val grounding = parseGrounding(raw)
            ?: return ToolResult.Error(
                call.callId,
                ErrorClass.UNKNOWN,
                "Vision response tidak parseable: ${raw.take(120)}",
            )
        if (!grounding.found) {
            return ToolResult.Error(
                call.callId,
                ErrorClass.SELECTOR_NOT_FOUND,
                "Vision LLM tidak ketemu '$query' di layar (conf=${grounding.confidence})",
            )
        }

        Timber.i("vision_tap '$query' → (${grounding.x},${grounding.y}) conf=${grounding.confidence}")
        val tapped = a11y.treeWalker.tapAt(grounding.x, grounding.y)
        return if (tapped) ToolResult.Success(
            callId = call.callId,
            data = JsonObject(mapOf(
                "x" to JsonPrimitive(grounding.x),
                "y" to JsonPrimitive(grounding.y),
                "confidence" to JsonPrimitive(grounding.confidence),
                "label" to JsonPrimitive(grounding.label),
            )),
        ) else ToolResult.Error(
            call.callId,
            ErrorClass.NOT_AVAILABLE,
            "dispatchGesture failed di koordinat (${grounding.x},${grounding.y})",
        )
    }

    private fun parseGrounding(raw: String): GroundingResult? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            val obj = json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject
            GroundingResult(
                x = obj["x"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                y = obj["y"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                confidence = obj["confidence"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
                label = obj["label"]?.jsonPrimitive?.content ?: "not_found",
            )
        }.getOrNull()
    }
}
