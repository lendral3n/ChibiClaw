package com.chibiclaw.perception.vision

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Phase 2.1 — transparent Activity whose only job is to host the OS
 * MediaProjection grant dialog. The user taps "Start now" and we forward
 * the (resultCode, data) pair to [ScreenCaptureController] which holds
 * onto it for the rest of the session.
 *
 * We can't call MediaProjectionManager.createScreenCaptureIntent() from a
 * background Service directly — the OS rejects it and closes the dialog
 * immediately. Activity is the only class type that can legally start a
 * projection request.
 */
@AndroidEntryPoint
class ScreenCaptureRequestActivity : ComponentActivity() {

    @Inject lateinit var controller: ScreenCaptureController

    private lateinit var manager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = manager.createScreenCaptureIntent()
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_CODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CODE) return
        if (resultCode == Activity.RESULT_OK && data != null) {
            controller.onGranted(resultCode, data)
            Log.d(TAG, "MediaProjection granted")
        } else {
            controller.onDenied()
            Log.w(TAG, "MediaProjection denied (resultCode=$resultCode)")
        }
        finish()
    }

    companion object {
        private const val TAG = "ScreenCaptureRequest"
        private const val REQUEST_CODE = 7821
    }
}
