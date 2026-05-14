package com.chibiclaw.vision.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * ML Kit Text Recognition v2 wrapper.
 *
 * Latin script default. Cina/Korea/Jepang ditambah Phase 9 polish kalau perlu.
 */
@Singleton
class MlKitOcr @Inject constructor() {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    data class Line(val text: String, val box: Rect, val confidence: Float)

    /** Plain-text extraction, urut top-bottom. */
    suspend fun extractText(bitmap: Bitmap): String {
        val lines = extractLines(bitmap)
        return lines.joinToString("\n") { it.text }
    }

    suspend fun extractLines(bitmap: Bitmap): List<Line> = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val lines = result.textBlocks.flatMap { block ->
                    block.lines.map { line ->
                        Line(
                            text = line.text,
                            box = line.boundingBox ?: Rect(),
                            confidence = 1f, // ML Kit doesn't expose per-line confidence
                        )
                    }
                }
                if (cont.isActive) cont.resume(lines)
            }
            .addOnFailureListener { e ->
                Timber.w(e, "ML Kit OCR failed")
                if (cont.isActive) cont.resume(emptyList())
            }
    }
}
