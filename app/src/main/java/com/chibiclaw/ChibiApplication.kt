package com.chibiclaw

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.chibiclaw.ai.llm.adapters.GeminiFreeAdapter
import com.chibiclaw.data.prefs.SecurePreferences
import com.chibiclaw.voice.tts.ElevenLabsTts
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Phase 0-9:
 * - Hilt @HiltAndroidApp untuk DI tree.
 * - Timber logging setup (debug tree).
 * - WorkManager config dengan HiltWorkerFactory.
 * - Phase 9: auto-populate API keys dari BuildConfig (sumber: local.properties)
 *   ke SecurePreferences kalau belum di-set (idempotent — user paste manual
 *   via setup wizard tetap override).
 *
 * Init service di ChibiService.onCreate() atau setelah user complete setup wizard.
 */
@HiltAndroidApp
class ChibiApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var securePreferences: SecurePreferences

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("ChibiApplication.onCreate()")
        autoPopulateApiKeys()
    }

    /**
     * Idempotent populate: hanya tulis kalau key belum ada di SecurePreferences
     * dan BuildConfig punya value (dari local.properties build-time).
     */
    private fun autoPopulateApiKeys() {
        if (BuildConfig.CHIBI_GEMINI_API_KEY.isNotBlank() &&
            securePreferences.getString(GeminiFreeAdapter.KEY_API).isNullOrBlank()
        ) {
            securePreferences.putString(GeminiFreeAdapter.KEY_API, BuildConfig.CHIBI_GEMINI_API_KEY)
            Timber.i("Gemini API key auto-populated dari BuildConfig")
        }
        if (BuildConfig.CHIBI_ELEVENLABS_API_KEY.isNotBlank() &&
            securePreferences.getString(ElevenLabsTts.KEY_ELEVENLABS_API_KEY).isNullOrBlank()
        ) {
            securePreferences.putString(ElevenLabsTts.KEY_ELEVENLABS_API_KEY, BuildConfig.CHIBI_ELEVENLABS_API_KEY)
            Timber.i("ElevenLabs API key auto-populated dari BuildConfig")
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
