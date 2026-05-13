package com.chibiclaw

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Phase 0:
 * - Hilt @HiltAndroidApp untuk DI tree.
 * - Timber logging setup (debug tree).
 * - WorkManager config dengan HiltWorkerFactory (untuk audit cleanup + future workers).
 *
 * Nothing heavy. Init service di ChibiService.onCreate() atau setelah user
 * complete setup wizard.
 */
@HiltAndroidApp
class ChibiApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("ChibiApplication.onCreate()")
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
