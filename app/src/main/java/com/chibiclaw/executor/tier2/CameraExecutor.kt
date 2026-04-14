package com.chibiclaw.executor.tier2

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import com.chibiclaw.executor.CaptureAction
import com.chibiclaw.perception.vision.BarcodeScanner
import com.chibiclaw.perception.vision.TextRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4.3 / 4.4 / 4.5 — camera + gallery operations.
 *
 * Opens the system camera activity for foto / video capture, or runs
 * ML Kit on a supplied image URI for barcode / OCR work. Gemma never
 * gets raw Bitmaps back — it gets a text summary (barcode payload, OCR
 * text, save path) that it can quote directly in the final report.
 */
@Singleton
class CameraExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val barcodeScanner: BarcodeScanner,
    private val textRecognizer: TextRecognizer
) {

    suspend fun perform(action: CaptureAction): String {
        return when (action.kind.lowercase()) {
            "photo" -> openCamera(MediaStore.ACTION_IMAGE_CAPTURE, "photo")
            "video" -> openCamera(MediaStore.ACTION_VIDEO_CAPTURE, "video")
            "qr_scan", "barcode" -> scanFromUri(action.args["uri"].orEmpty())
            "ocr_gallery", "ocr_image" -> ocrFromUri(action.args["uri"].orEmpty())
            else -> "capture_error: unknown kind=${action.kind}"
        }
    }

    private fun openCamera(action: String, label: String): String {
        return try {
            val intent = Intent(action).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "${label}_opened"
        } catch (e: Exception) {
            "capture_error: ${e.message}"
        }
    }

    private suspend fun scanFromUri(uri: String): String {
        if (uri.isBlank()) return "capture_error: empty_uri"
        return try {
            val parsed = Uri.parse(uri)
            val result = barcodeScanner.scan(context, parsed)
            result ?: "barcode_not_found"
        } catch (e: Exception) {
            "capture_error: ${e.message}"
        }
    }

    private suspend fun ocrFromUri(uri: String): String {
        if (uri.isBlank()) return "capture_error: empty_uri"
        return try {
            val parsed = Uri.parse(uri)
            val text = textRecognizer.recognize(context, parsed)
            if (text.isBlank()) "ocr_empty" else "ocr:\n$text"
        } catch (e: Exception) {
            "capture_error: ${e.message}"
        }
    }
}
