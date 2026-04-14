package com.chibiclaw.core

import android.app.Application
import android.util.Log
import com.chibiclaw.gateway.source.CronSource
import com.chibiclaw.safety.WhitelistManager
import com.chibiclaw.service.ShizukuHandler
import com.chibiclaw.skills.SkillRegistry
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import javax.inject.Inject

/**
 * Phase 10 — lean [Application]. All **heavy** initialization (Gemma
 * model load, [com.chibiclaw.service.ChibiService] start, orchestrator
 * warm-up) has moved into [ChibiBootstrapper] and is only triggered
 * **after** the user picks a mode. onCreate() here now only runs
 * cheap, always-safe setup:
 *
 *   - skill registry (JSON manifest parse, microseconds)
 *   - Shizuku binder ping (cheap IPC check)
 *   - whitelist defaults (I/O on IO dispatcher)
 *   - cron restore (WorkManager enqueue, I/O on IO dispatcher)
 *
 * The old `autoLoadModel()` call is **gone** — the Gemma engine is
 * now loaded lazily from [ChibiBootstrapper.bootstrap] which runs
 * behind the Mode Selection screen's "Loading..." step.
 */
@HiltAndroidApp
class ChibiApp : Application() {

    @Inject lateinit var shizukuHandler: ShizukuHandler
    @Inject lateinit var skillRegistry: SkillRegistry
    @Inject lateinit var whitelistManager: WhitelistManager
    @Inject lateinit var cronSource: CronSource

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ChibiClaw v3 starting (lazy mode)...")
        // Tiny, always-safe bootstrap. Heavy init happens in
        // ChibiBootstrapper after the mode-select screen.
        skillRegistry.initialize()
        initShizuku()
        scope.launch { whitelistManager.initDefaults() }
        scope.launch {
            runCatching { cronSource.restoreAll() }
                .onFailure { Log.w(TAG, "cronSource.restoreAll failed: ${it.message}") }
        }
    }

    private fun initShizuku() {
        if (Shizuku.pingBinder()) {
            shizukuHandler.bindService()
            Log.d(TAG, "Shizuku available — binding service")
        } else {
            Log.w(TAG, "Shizuku not available (optional)")
        }
    }

    companion object {
        private const val TAG = "ChibiApp"
    }
}
