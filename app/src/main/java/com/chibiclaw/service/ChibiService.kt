package com.chibiclaw.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.chibiclaw.ai.GemmaEngineManager
import com.chibiclaw.ai.ModelLibrary
import com.chibiclaw.ai.ModelTier
import com.chibiclaw.api.IChibiCallback
import com.chibiclaw.api.IChibiService
import com.chibiclaw.core.ChibiOrchestrator
import com.chibiclaw.gateway.CommandGateway
import com.chibiclaw.gateway.CommandSource
import com.chibiclaw.gateway.source.CronSource
import com.chibiclaw.memory.pref.SecurePreferences
import com.chibiclaw.state.ChibiState
import com.chibiclaw.state.ChibiStateMachine
import com.chibiclaw.service.FloatingOverlay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ChibiService : Service() {

    @Inject lateinit var commandGateway: CommandGateway
    @Inject lateinit var stateMachine: ChibiStateMachine
    @Inject lateinit var orchestrator: ChibiOrchestrator
    @Inject lateinit var floatingOverlay: FloatingOverlay
    @Inject lateinit var securePreferences: SecurePreferences
    @Inject lateinit var cronSource: CronSource
    @Inject lateinit var gemmaEngineManager: GemmaEngineManager
    @Inject lateinit var modelLibrary: ModelLibrary

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val callbacks = mutableListOf<IChibiCallback>()

    /**
     * Receives fire-events from [com.chibiclaw.gateway.source.CronWorker].
     * Every scheduled task resolves to a broadcast in doWork(), which this
     * receiver translates into a normal [CommandGateway.submitDirect] call so
     * the rest of the orchestrator pipeline treats it exactly like any other
     * input source.
     *
     * Without this receiver every persisted cron task would fire from
     * WorkManager, log success, and then drop the command on the floor.
     */
    private val cronReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != ACTION_CRON_COMMAND) return
            val command = intent.getStringExtra("command") ?: return
            val taskId = intent.getStringExtra("task_id").orEmpty()
            Log.d(TAG, "Cron broadcast received: id=$taskId command=\"$command\"")
            serviceScope.launch {
                commandGateway.submitDirect(command, CommandSource.CRON)
                if (taskId.isNotEmpty()) {
                    runCatching { cronSource.markRun(taskId) }
                }
            }
        }
    }

    private fun getCallerPackage(): String? {
        val uid = Binder.getCallingUid()
        return packageManager.getNameForUid(uid)
    }

    private fun isCallerAllowed(): Boolean {
        val caller = getCallerPackage() ?: return false
        val whitelist = securePreferences.getCallerWhitelist()
        // Allow self (same package) always
        if (caller == packageName) return true
        val allowed = whitelist.isEmpty() || caller in whitelist
        if (!allowed) Log.w(TAG, "AIDL call BLOCKED from: $caller (not in whitelist)")
        else Log.d(TAG, "AIDL call from: $caller [UID=${Binder.getCallingUid()}]")
        return allowed
    }

    private val binder = object : IChibiService.Stub() {
        override fun sendCommand(jsonCommand: String) {
            if (!isCallerAllowed()) return
            serviceScope.launch {
                commandGateway.submitFromAidl(jsonCommand)
            }
        }

        override fun getStatus(): String = stateMachine.current.name

        override fun stopCurrentTask() {
            commandGateway.stopCurrent()
            stateMachine.reset()
        }

        override fun pauseCurrentTask() {
            stateMachine.transition(ChibiState.PAUSED, "user pause")
        }

        override fun resumeCurrentTask() {
            stateMachine.transition(ChibiState.EXECUTING, "user resume")
        }

        override fun registerCallback(callback: IChibiCallback?) {
            callback?.let { synchronized(callbacks) { callbacks.add(it) } }
        }

        override fun unregisterCallback(callback: IChibiCallback?) {
            callback?.let { synchronized(callbacks) { callbacks.remove(it) } }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Phase 10 fix — on Android 14+ (targetSDK ≥ 34) the manifest declares
        // `specialUse|mediaProjection` so we have the option to upgrade the
        // service type later when ScreenCaptureBridge actually needs to grab
        // frames. At initial startup we MUST start with FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        // only — the mediaProjection subtype requires an active projection
        // consent token (CAPTURE_VIDEO_OUTPUT / android:project_media) which
        // we don't have yet, and the system throws SecurityException if we
        // include it here. The old two-argument startForeground() call
        // implicitly passed every declared type and crashed on every boot.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
        observeState()
        orchestrator.start()
        floatingOverlay.start()
        registerCronReceiver()
        preloadE2BOnBoot()
        Log.d(TAG, "ChibiService started")
    }

    /**
     * **P4.2 — Pre-load E2B on boot.**
     *
     * [ChibiApp.autoLoadModel] already warms the active model into the E4B
     * slot during [android.app.Application.onCreate]. This complementary
     * step kicks off a background coroutine that looks for a second,
     * lighter model in [ModelLibrary] and loads it into the E2B slot so
     * simple commands routed to E2B by [com.chibiclaw.ai.ModelRouter] pay
     * zero cold-load latency on the first tap.
     *
     * Design choices:
     *   - **Dispatchers.IO** so we never block the service thread on file
     *     reads or native engine init.
     *   - Fires after a **2-second delay** so Application.onCreate's E4B
     *     load gets the dispatcher first — we don't want to race two
     *     LiteRT-LM engines on the same GPU context during cold boot.
     *   - **Silent on failure**: every branch that can't load (no
     *     candidate / file gone / battery low / engine error) just
     *     logs and returns. The user never sees a toast for a feature
     *     they didn't explicitly request.
     *   - **Checks isReady(E2B) first** so a respawned service after OOM
     *     kill doesn't re-enqueue a load that's already live.
     */
    private fun preloadE2BOnBoot() {
        serviceScope.launch(Dispatchers.IO) {
            // Give E4B a head start — avoid two engines racing for GPU /
            // file handles during cold boot.
            delay(2_000L)

            if (gemmaEngineManager.isReady(ModelTier.E2B)) {
                Log.d(TAG, "preloadE2BOnBoot: E2B already ready, skipping")
                return@launch
            }

            val candidate = modelLibrary.e2bCandidate()
            if (candidate == null) {
                Log.d(TAG, "preloadE2BOnBoot: no E2B candidate in library (need a second <3GB model)")
                return@launch
            }
            if (!File(candidate.path).exists()) {
                Log.w(TAG, "preloadE2BOnBoot: candidate file missing → ${candidate.path}")
                return@launch
            }

            Log.d(TAG, "preloadE2BOnBoot: loading '${candidate.name}' → E2B slot [${candidate.sizeDisplay}]")
            val backend = securePreferences.getModelBackend()
            gemmaEngineManager.loadModel(
                modelPath = candidate.path,
                backendName = backend,
                tier = ModelTier.E2B
            )
        }
    }

    private fun registerCronReceiver() {
        val filter = IntentFilter(ACTION_CRON_COMMAND)
        // RECEIVER_NOT_EXPORTED — only our own CronWorker (same package) sends
        // this broadcast; no external caller should be able to trigger it.
        ContextCompat.registerReceiver(
            this,
            cronReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onDestroy() {
        floatingOverlay.stop()
        runCatching { unregisterReceiver(cronReceiver) }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeState() {
        serviceScope.launch {
            stateMachine.state.collect { state ->
                synchronized(callbacks) {
                    callbacks.forEach { cb ->
                        try { cb.onStateChanged(state.name) } catch (_: Exception) {}
                    }
                }
                updateNotification(state)
            }
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "chibi_service"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "ChibiClaw", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("ChibiClaw")
            .setContentText("Ready")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(state: ChibiState) {
        val nm = getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(this, "chibi_service")
            .setContentTitle("ChibiClaw — ${state.name}")
            .setContentText(if (state == ChibiState.IDLE) "Ready" else "Working...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        nm.notify(NOTIF_ID, notif)
    }

    companion object {
        private const val TAG = "ChibiService"
        private const val NOTIF_ID = 1001
        private const val ACTION_CRON_COMMAND = "com.chibiclaw.CRON_COMMAND"
    }
}
