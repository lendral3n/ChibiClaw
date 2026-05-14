package com.chibiclaw.vision.screenshot

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility resize, crop, encode untuk pipeline vision.
 *
 * Resize default ke 1024 max-dim — sweet spot untuk MiniCPM-V (latency vs detail)
 * dan Gemini Flash multimodal (max 3072px tapi 1024 cukup untuk grounding).
 *
 * Quality JPEG 85 — bagus balance size vs visual fidelity untuk OCR.
 */
@Singleton
class ImageProcessor @Inject constructor() {

    fun resize(src: Bitmap, maxDim: Int = MAX_DIM_DEFAULT): Bitmap {
        if (src.width <= maxDim && src.height <= maxDim) return src
        val scale = maxDim.toFloat() / maxOf(src.width, src.height)
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    fun crop(src: Bitmap, region: Rect): Bitmap? {
        val safe = Rect(
            region.left.coerceIn(0, src.width - 1),
            region.top.coerceIn(0, src.height - 1),
            region.right.coerceIn(1, src.width),
            region.bottom.coerceIn(1, src.height),
        )
        if (safe.width() <= 0 || safe.height() <= 0) return null
        return Bitmap.createBitmap(src, safe.left, safe.top, safe.width(), safe.height())
    }

    fun toJpegBytes(bitmap: Bitmap, quality: Int = JPEG_QUALITY): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), stream)
        return stream.toByteArray()
    }

    fun toBase64(bitmap: Bitmap, quality: Int = JPEG_QUALITY): String =
        Base64.encodeToString(toJpegBytes(bitmap, quality), Base64.NO_WRAP)

    companion object {
        const val MAX_DIM_DEFAULT = 1024
        const val JPEG_QUALITY = 85
    }
}
