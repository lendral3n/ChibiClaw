package com.chibiclaw.vision.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.view.WindowInsets
import com.chibiclaw.accessibility.ChibiAccessibilityService
import com.chibiclaw.vision.projection.ChibiProjectionManager
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val projection: ChibiProjectionManager,
    private val processor: ImageProcessor,
) {

    private val mutex = Mutex()
    @Volatile private var cachedRaw: Bitmap? = null
    @Volatile private var cachedAtMs: Long = 0L

    /**
     * Snapshot Bitmap penuh (native resolution). Cache 1 detik.
     *
     * Phase 9 IME guard: kalau keyboard visible (kemungkinan password field
     * exposed), refuse capture defensive privacy.
     */
    suspend fun snapshotRaw(forceFresh: Boolean = false): Bitmap? = mutex.withLock {
        if (isImeLikelyVisible()) {
            Timber.w("Screen capture refused — IME likely visible (privacy guard)")
            return@withLock null
        }
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

    /**
     * Detect IME via Accessibility windows (lebih reliable dari InputMethodManager
     * yang hanya tau IME milik aplikasi sendiri).
     */
    private fun isImeLikelyVisible(): Boolean {
        val a11y = ChibiAccessibilityService.instance() ?: return false
        return runCatching {
            a11y.windows.any { window ->
                window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD
            }
        }.getOrDefault(false)
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
