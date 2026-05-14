package com.chibiclaw.vision.projection

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.chibiclaw.data.prefs.SecurePreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Inject

/**
 * Translucent activity yang request MediaProjection permission via system
 * dialog, lalu persist token via ProjectionTokenStore lalu finish.
 *
 * Dipanggil dari Setup wizard step Vision (Phase 5) atau dari notification
 * action saat token expired.
 *
 * Hilt @AndroidEntryPoint — supaya bisa @Inject store + audit. Tetap pakai
 * EntryPoint pattern untuk akses application-level dependencies kalau perlu
 * dari non-Hilt callsite.
 */
@AndroidEntryPoint
class MediaProjectionPermissionActivity : ComponentActivity() {

    @Inject lateinit var tokenStore: ProjectionTokenStore

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            tokenStore.saveToken(result.resultCode, result.data!!)
            Timber.i("MediaProjection granted by user")
            setResult(Activity.RESULT_OK)
        } else {
            Timber.w("MediaProjection denied / cancelled")
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        launcher.launch(mpm.createScreenCaptureIntent())
    }

    /**
     * Helper: EntryPoint untuk akses dari konteks non-Hilt (mis. tool yang
     * trigger ulang permission request lewat Intent).
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TokenStoreEntryPoint {
        fun projectionTokenStore(): ProjectionTokenStore
        fun securePreferences(): SecurePreferences
    }

    companion object {
        fun launchIntent(app: Application): Intent =
            Intent(app, MediaProjectionPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        fun tokenStoreFromApp(app: Application): ProjectionTokenStore =
            EntryPointAccessors.fromApplication(app, TokenStoreEntryPoint::class.java)
                .projectionTokenStore()
    }
}
