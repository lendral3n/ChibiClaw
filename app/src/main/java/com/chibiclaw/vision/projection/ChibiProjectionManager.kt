package com.chibiclaw.vision.projection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * ChibiProjectionManager — orchestrate MediaProjection lifecycle:
 *   - Recreate MediaProjection dari saved token (di-call dari ChibiService.onCreate)
 *   - Capture single frame on-demand → Bitmap
 *   - Stop projection (lifecycle observer kalau service di-kill)
 *
 * Capture pattern: VirtualDisplay + ImageReader. Reuse VirtualDisplay across
 * captures supaya tidak perlu tear-down per shot (latency <100ms target).
 *
 * Thread-safety: single mutex guard. Capture call dari coroutine context;
 * ImageReader callback delivered via HandlerThread.
 */
@Singleton
class ChibiProjectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: ProjectionTokenStore,
) {

    @Volatile private var mediaProjection: MediaProjection? = null
    @Volatile private var virtualDisplay: VirtualDisplay? = null
    @Volatile private var imageReader: ImageReader? = null
    private val handlerThread = HandlerThread("ChibiProjection").also { it.start() }
    private val handler = Handler(handlerThread.looper)

    private val mpm: MediaProjectionManager
        get() = context.getSystemService(MediaProjectionManager::class.java)

    private val metrics: DisplayMetrics
        get() {
            val wm = context.getSystemService(WindowManager::class.java)
            val display = wm.defaultDisplay
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(m)
            return m
        }

    /** True kalau token tersedia DAN MediaProjection berhasil di-recreate. */
    fun isReady(): Boolean = mediaProjection != null || tryRecreate()

    fun hasToken(): Boolean = tokenStore.hasToken()

    /**
     * Recreate MediaProjection dari saved token. Idempotent — kalau sudah
     * hidup, no-op.
     */
    @Synchronized
    fun tryRecreate(): Boolean {
        if (mediaProjection != null) return true
        val data = tokenStore.loadResultData() ?: return false
        val code = tokenStore.loadResultCode()
        return runCatching {
            val mp = mpm.getMediaProjection(code, data)
                ?: error("MediaProjectionManager.getMediaProjection returned null")
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Timber.w("MediaProjection.onStop — token revoked")
                    teardown()
                }
            }, handler)
            mediaProjection = mp
            ensureVirtualDisplay()
            true
        }.onFailure {
            Timber.w(it, "MediaProjection recreate failed; token mungkin stale")
            tokenStore.clear()
        }.getOrDefault(false)
    }

    @Synchronized
    private fun ensureVirtualDisplay() {
        if (virtualDisplay != null) return
        val mp = mediaProjection ?: return
        val m = metrics
        val width = m.widthPixels
        val height = m.heightPixels
        val density = m.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        virtualDisplay = mp.createVirtualDisplay(
            "ChibiVisionCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler,
        )
        Timber.i("VirtualDisplay ready ${width}x${height} @${density}dpi")
    }

    /**
     * Capture single Bitmap. Suspend sampai frame tersedia atau timeout.
     */
    suspend fun captureBitmap(timeoutMs: Long = 3_000): Bitmap? {
        if (!tryRecreate()) return null
        val reader = imageReader ?: return null

        return suspendCancellableCoroutine { cont ->
            val timeoutRunnable = Runnable {
                runCatching { reader.setOnImageAvailableListener(null, null) }
                if (cont.isActive) cont.resume(null)
            }
            handler.postDelayed(timeoutRunnable, timeoutMs)

            reader.setOnImageAvailableListener({ r ->
                handler.removeCallbacks(timeoutRunnable)
                val image = runCatching { r.acquireLatestImage() }.getOrNull()
                val bitmap = image?.let { extractBitmap(it) }
                image?.close()
                runCatching { r.setOnImageAvailableListener(null, null) }
                if (cont.isActive) cont.resume(bitmap)
            }, handler)
        }
    }

    private fun extractBitmap(image: Image): Bitmap? = runCatching {
        val planes = image.planes
        if (planes.isEmpty()) return@runCatching null
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888,
        )
        bitmap.copyPixelsFromBuffer(buffer)
        if (rowPadding == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }.onFailure { Timber.w(it, "extractBitmap failed") }.getOrNull()

    @Synchronized
    fun teardown() {
        runCatching { virtualDisplay?.release() }
        runCatching { imageReader?.close() }
        runCatching { mediaProjection?.stop() }
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }
}
