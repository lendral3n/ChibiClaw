package com.chibiclaw.executor.tier3

import com.chibiclaw.executor.VisionAnalyzeAction
import com.chibiclaw.perception.vision.GemmaVisionAnalyzer
import com.chibiclaw.perception.vision.ScreenCapture
import com.chibiclaw.perception.vision.ScreenCaptureController
import com.chibiclaw.perception.vision.TextRecognizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2.2 — thin adapter that routes a [VisionAnalyzeAction] to either
 * ML Kit OCR (for mode=ocr, cheap and deterministic) or Gemma multimodal
 * (for describe / find_element / count, which need reasoning).
 *
 * Keeping the routing here means [GemmaVisionAnalyzer] stays focused on
 * "things that need the LLM" and [TextRecognizer] stays focused on
 * "things that just need pixels". The executor also centralises the
 * MediaProjection precondition check so each implementation can assume a
 * bitmap is available by the time it's called.
 */
@Singleton
class VisionActionExecutor @Inject constructor(
    private val gemmaVisionAnalyzer: GemmaVisionAnalyzer,
    private val textRecognizer: TextRecognizer,
    private val screenCapture: ScreenCapture,
    private val screenCaptureController: ScreenCaptureController
) {

    suspend fun analyze(action: VisionAnalyzeAction): String {
        // Vision always needs a live MediaProjection. If the user hasn't
        // granted yet we launch the request activity exactly once and
        // wait up to ~20 s for them to tap "Start now". Subsequent calls
        // are free because the grant is cached in the controller.
        if (!screenCaptureController.isGranted()) {
            val ok = screenCaptureController.requestGrant()
            if (!ok) return "vision_error: user_denied_screen_capture"
        }
        // Delegate the capture itself to the analyzer/recognizer so
        // orientation + scale stay consistent with the bitmap format each
        // path expects.
        return when (action.mode.lowercase()) {
            "ocr" -> ocrFallback(action)
            else -> gemmaVisionAnalyzer.analyze(action.mode, action.query)
        }
    }

    private suspend fun ocrFallback(action: VisionAnalyzeAction): String {
        val bitmap = try {
            screenCapture.capture()
        } catch (e: Exception) {
            null
        } ?: return "[VISION: screenshot capture failed — grant MediaProjection]"
        val text = try {
            textRecognizer.recognizeFromBitmap(bitmap)
        } catch (e: Exception) {
            // If ML Kit fails, fall back to the Gemma OCR path so the user
            // still gets *something* instead of a stacktrace.
            return gemmaVisionAnalyzer.analyze("ocr", action.query)
        }
        return if (text.isBlank()) "[VISION_OCR: empty]" else "[VISION_OCR]\n$text\n[/VISION_OCR]"
    }
}
