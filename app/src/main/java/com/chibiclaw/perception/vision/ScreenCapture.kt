package com.chibiclaw.perception.vision

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenCapture @Inject constructor() {

    private var mediaProjection: MediaProjection? = null

    fun setMediaProjection(projection: MediaProjection) {
        mediaProjection = projection
    }

    fun capture(width: Int = 1080, height: Int = 1920, dpi: Int = 320): Bitmap? {
        val projection = mediaProjection ?: run {
            Log.w(TAG, "MediaProjection not set — capture unavailable")
            return null
        }
        return try {
            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
            val virtualDisplay: VirtualDisplay? = projection.createVirtualDisplay(
                "ChibiCapture", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface, null, null
            )
            Thread.sleep(200) // allow frame to render
            val image = imageReader.acquireLatestImage()
            val bitmap = image?.let {
                val planes = it.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width
                val bmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(buffer)
                it.close()
                bmp
            }
            virtualDisplay?.release()
            imageReader.close()
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "ScreenCapture"
    }
}
