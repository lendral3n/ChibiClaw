package com.chibiclaw.perception.vision

import android.graphics.Bitmap
import android.util.Log
import com.chibiclaw.ai.GemmaEngineManager
import com.chibiclaw.ai.GemmaInference
import kotlinx.coroutines.flow.toList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P3.1 / Phase 2.2 — GemmaVisionAnalyzer
 *
 * Runs Gemma multimodal inference on screenshot bitmaps. Provides four modes:
 *  - describe: natural-language description of the entire screen
 *  - findElement: locate an element described by a query and return its
 *                 approximate bounds + a short natural description
 *  - ocr: extract every visible chunk of text (mainly a fallback when the
 *         ML Kit OCR executor isn't available)
 *  - count: count how many occurrences of a described element are visible
 *
 * Vision is pinned to E4B inside [GemmaInference.sendMultimodal] — the E2B
 * tier doesn't have the vision tower. The analyzer is stateless; the
 * *conversation* session lives inside GemmaInference and is reused across
 * calls so KV cache stays warm.
 *
 * The public API intentionally does NOT accept a [com.chibiclaw.ai.ChibiClawTools]
 * — vision analysis should never trigger tool calls, so passing `null`
 * keeps the conversation in pure text-generation mode and avoids the
 * parser-bug fallback path inside GemmaInference.
 */
@Singleton
class GemmaVisionAnalyzer @Inject constructor(
    private val engineManager: GemmaEngineManager,
    private val gemmaInference: GemmaInference,
    private val screenCapture: ScreenCapture
) {

    /** Entry point used by VisionAnalyzeAction / tools. */
    suspend fun analyze(mode: String, query: String): String {
        val bitmap = captureOrFail() ?: return "[VISION: screenshot capture failed — grant MediaProjection]"
        return when (mode.lowercase()) {
            "describe" -> describe(bitmap)
            "find_element", "find" -> findElement(bitmap, query)
            "ocr" -> ocr(bitmap)
            "count" -> count(bitmap, query)
            else -> describe(bitmap)
        }
    }

    /** Legacy entry point (used by perception router). Returns a VISION_MAP block. */
    suspend fun analyzeCurrentScreen(): String {
        val bitmap = captureOrFail() ?: return "[VISION: Screenshot capture failed]"
        return describe(bitmap)
    }

    suspend fun analyzeScreenshot(bitmap: Bitmap): String = describe(bitmap)

    private suspend fun describe(bitmap: Bitmap): String {
        if (!engineManager.isReady()) return "[VISION: Engine not ready]"
        val systemPrompt = """
            You are a UI analyzer. Describe ALL visible UI elements in the screenshot.
            Output format (one element per line):
              TYPE "label" @(centerX,centerY) [bounds=l,t,r,b]
            Types: BTN, INPUT, TEXT, IMG, SCROLL, TAB, ICON.
            Be concise. Only list elements that a user could interact with.
        """.trimIndent()
        val userMsg = "Describe every interactive UI element in this screenshot."
        return runInference(bitmap, systemPrompt, userMsg, tag = "VISION_DESCRIBE")
    }

    private suspend fun findElement(bitmap: Bitmap, query: String): String {
        if (!engineManager.isReady()) return "[VISION: Engine not ready]"
        if (query.isBlank()) return "[VISION: find_element needs a query]"
        val systemPrompt = """
            You are a UI element locator. The user will describe ONE element
            they want to find on the screenshot. Return the single best match
            as a one-liner in this exact format:
              FOUND "label" @(centerX,centerY) [bounds=l,t,r,b] confidence=0.0-1.0
            If nothing matches, return exactly:
              NOT_FOUND
            Do not explain. Do not add any other text.
        """.trimIndent()
        val userMsg = "Find: $query"
        return runInference(bitmap, systemPrompt, userMsg, tag = "VISION_FIND")
    }

    private suspend fun ocr(bitmap: Bitmap): String {
        if (!engineManager.isReady()) return "[VISION: Engine not ready]"
        val systemPrompt =
            "You are an OCR engine. Output ALL text visible in the image, " +
                "preserving line breaks but no other commentary."
        val userMsg = "Extract all text from this screenshot."
        return runInference(bitmap, systemPrompt, userMsg, tag = "VISION_OCR")
    }

    private suspend fun count(bitmap: Bitmap, query: String): String {
        if (!engineManager.isReady()) return "[VISION: Engine not ready]"
        if (query.isBlank()) return "[VISION: count needs a query]"
        val systemPrompt =
            "You count UI elements. Return exactly one line: " +
                "\"COUNT=<integer>\" where <integer> is the number of matches."
        val userMsg = "How many \"$query\" are visible in this screenshot?"
        return runInference(bitmap, systemPrompt, userMsg, tag = "VISION_COUNT")
    }

    private suspend fun runInference(
        bitmap: Bitmap,
        systemPrompt: String,
        userMsg: String,
        tag: String
    ): String {
        return try {
            val chunks = gemmaInference
                .sendMultimodal(
                    userText = userMsg,
                    screenshot = bitmap,
                    systemPrompt = systemPrompt,
                    tools = null
                )
                .toList()
            val result = chunks.joinToString("").trim()
            Log.d(TAG, "$tag → ${result.take(200)}")
            if (result.isEmpty()) "[$tag: empty]" else "[$tag]\n$result\n[/$tag]"
        } catch (e: Exception) {
            Log.e(TAG, "$tag error: ${e.message}")
            "[$tag: ${e.message}]"
        }
    }

    private suspend fun captureOrFail(): Bitmap? {
        return try {
            screenCapture.capture()
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot failed: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "GemmaVisionAnalyzer"
    }
}
