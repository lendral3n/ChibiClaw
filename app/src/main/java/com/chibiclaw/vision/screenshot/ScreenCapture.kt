package com.chibiclaw.vision.screenshot

import android.graphics.Bitmap
import com.chibiclaw.vision.projection.ChibiProjectionManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ScreenCapture — high-level wrapper di atas ChibiProjectionManager.
 *
 * Cache: hold last bitmap selama 1 detik supaya multiple tool call dalam
 * window pendek (vision_describe → vision_extract_text) reuse same shot.
 *
 * Returned Bitmap di-resize via ImageProcessor sebelum di-feed ke LLM
 * (downstream caller).
 */
@Singleton
class ScreenCapture @Inject constructor(
    private val projection: ChibiProjectionManager,
    private val processor: ImageProcessor,
) {

    private val mutex = Mutex()
    @Volatile private var cachedRaw: Bitmap? = null
    @Volatile private var cachedAtMs: Long = 0L

    /** Snapshot Bitmap penuh (native resolution). Cache 1 detik. */
    suspend fun snapshotRaw(forceFresh: Boolean = false): Bitmap? = mutex.withLock {
        val now = System.currentTimeMillis()
        if (!forceFresh && cachedRaw != null && (now - cachedAtMs) < CACHE_TTL_MS) {
            return@withLock cachedRaw
        }
        val fresh = projection.captureBitmap()
        if (fresh == null) {
            Timber.w("captureBitmap returned null — token mungkin invalid")
            return@withLock null
        }
        cachedRaw = fresh
        cachedAtMs = now
        fresh
    }

    /** Snapshot resized untuk LLM input (1024 max-dim default). */
    suspend fun snapshotResized(maxDim: Int = ImageProcessor.MAX_DIM_DEFAULT): Bitmap? =
        snapshotRaw()?.let { processor.resize(it, maxDim) }

    fun isAvailable(): Boolean = projection.hasToken()

    fun invalidateCache() {
        cachedRaw = null
        cachedAtMs = 0L
    }

    companion object {
        private const val CACHE_TTL_MS = 1_000L
    }
}
