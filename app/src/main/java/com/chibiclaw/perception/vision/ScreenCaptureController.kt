package com.chibiclaw.perception.vision

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2.1 — singleton that owns the MediaProjection lifecycle and
 * hands the live [MediaProjection] instance to [ScreenCapture]. Also
 * exposes [grantedFlow] so the UI can render an indicator in the
 * Dashboard ("screen capture granted / denied / not requested").
 *
 * The grant flow:
 *  1. Caller invokes [requestGrant] — this launches [ScreenCaptureRequestActivity].
 *  2. The activity calls [onGranted] with the result intent.
 *  3. We instantiate a MediaProjection, attach a required
 *     [MediaProjection.Callback] (Android 14+ hard requirement), and push
 *     it into [ScreenCapture] so subsequent capture() calls work.
 *  4. [awaitGrant] suspends any pending callers until a decision is made.
 */
@Singleton
class ScreenCaptureController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val screenCapture: ScreenCapture
) {
    private val _granted = MutableStateFlow(false)
    val grantedFlow: StateFlow<Boolean> = _granted

    @Volatile
    private var pending: CompletableDeferred<Boolean>? = null

    @Volatile
    private var projection: MediaProjection? = null

    fun isGranted(): Boolean = projection != null

    /**
     * Launches the system grant UI. Returns true once the user taps
     * "Start now", false on any denial or failure. Safe to call even
     * when a projection is already active — returns true immediately.
     */
    suspend fun requestGrant(): Boolean {
        if (isGranted()) return true
        pending?.let { return it.await() }
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        try {
            val intent = Intent(context, ScreenCaptureRequestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch request activity: ${e.message}")
            pending = null
            return false
        }
        return deferred.await()
    }

    fun onGranted(resultCode: Int, data: Intent) {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            as MediaProjectionManager
        val mp: MediaProjection? = try {
            manager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Log.e(TAG, "getMediaProjection failed: ${e.message}")
            null
        }
        if (mp == null) {
            Log.e(TAG, "getMediaProjection returned null — treating as denied")
            onDenied()
            return
        }
        // Android 14+ requires a registered callback BEFORE createVirtualDisplay.
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(TAG, "MediaProjection stopped")
                revoke()
            }
        }, null)
        projection = mp
        screenCapture.setMediaProjection(mp)
        _granted.value = true
        pending?.complete(true)
        pending = null
    }

    fun onDenied() {
        _granted.value = false
        pending?.complete(false)
        pending = null
    }

    fun revoke() {
        try {
            projection?.stop()
        } catch (_: Exception) {}
        projection = null
        _granted.value = false
    }

    companion object {
        private const val TAG = "ScreenCaptureCtrl"
    }
}
