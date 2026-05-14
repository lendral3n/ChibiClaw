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
import com.chibiclaw.vision.llm.MiniCPMVInference
import com.chibiclaw.vision.llm.VisionPromptBuilder
import com.chibiclaw.vision.screenshot.ScreenCapture
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * vision_describe — short text answer dari MiniCPM-V untuk pertanyaan tentang
 * layar (mis. "Apa username yang ditampilkan?", "berapa unread di WA?").
 */
class VisionDescribeTool @Inject constructor(
    private val screenCapture: ScreenCapture,
    private val visionLlm: MiniCPMVInference,
) : Tool {

    override val spec = ToolSpec(
        name = "vision_describe",
        description = """
            Describe konten layar secara natural-language untuk query tertentu.
            Pakai saat: a11y tree tidak lengkap, butuh konteks visual
            (warna badge, count notifikasi non-textual, layout state).
        """.trimIndent(),
        parameters = mapOf(
            "query" to "string (pertanyaan, e.g. 'apa pesan terakhir di WA Budi')",
        ),
        capability = ToolCapability(
            latencyMsRange = 1500..4000,
            worksOn = listOf("any_app_screen"),
            requiresPermission = listOf("MediaProjection"),
            cost = ToolCost.MEDIUM,
        ),
        safety = ToolSafety(
            severity = ToolSeverity.MEDIUM,
            reason = "Screenshot diproses lokal — tidak persistent.",
        ),
    )

    override fun isAvailable(): Boolean = screenCapture.isAvailable()

    override suspend fun execute(call: ToolCall): ToolResult {
        val query = call.args["query"]?.jsonPrimitive?.content
            ?: return ToolResult.Error(call.callId, ErrorClass.INVALID_ARGS, "query arg required")

        val bitmap = screenCapture.snapshotRaw() ?: return ToolResult.Error(
            call.callId,
            ErrorClass.NOT_AVAILABLE,
            "Capture screenshot gagal",
            recoveryHint = "Re-grant MediaProjection",
        )
        val raw = visionLlm.infer(VisionPromptBuilder.describe(query), bitmap, maxTokens = 256)
            ?: return ToolResult.Error(
                call.callId,
                ErrorClass.NOT_AVAILABLE,
                "Vision LLM belum siap — push model atau escalate_to_cloud target=GEMINI",
            )

        return ToolResult.Success(
            callId = call.callId,
            data = JsonObject(mapOf(
                "description" to JsonPrimitive(raw.trim()),
            )),
        )
    }
}
