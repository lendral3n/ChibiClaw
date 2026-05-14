package com.chibiclaw.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.chibiclaw.R
import com.chibiclaw.agent.AgentRuntime
import com.chibiclaw.agent.initiative.InitiativeEngine
import com.chibiclaw.compliance.AuditLogger
import com.chibiclaw.memory.miner.MemoryWorkScheduler
import com.chibiclaw.data.database.AuditActionType
import com.chibiclaw.service.overlay.OverlayWindowManager
import com.chibiclaw.ui.MainActivity
import com.chibiclaw.vision.llm.MiniCPMVInference
import com.chibiclaw.vision.projection.ChibiProjectionManager
import com.chibiclaw.world.observers.AppLaunchDetector
import com.chibiclaw.world.observers.CalendarEventObserver
import com.chibiclaw.world.observers.NetworkObserver
import com.chibiclaw.world.observers.NotificationEventBridge
import com.chibiclaw.world.observers.SystemEventReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber
import javax.inject.Inject

/**
 * ChibiService — foreground service utama.
 *
 * Phase 0:
 * - FGS notification channel + notification
 * - Overlay window manager + bubble visible
 * - Audit log SERVICE_STARTED / STOPPED
 *
 * Phase 1+:
 * - Mount AgentRuntime + TaskManager
 * - Foreground service type upgrade ke "microphone|specialUse|mediaPlayback"
 * - Pipe wake / chat input ke AgentRuntime
 *
 * Service di-bind oleh MainActivity (untuk komunikasi UI ↔ service) tapi
 * juga survive lifecycle activity (start sticky).
 */
@AndroidEntryPoint
class ChibiService : Service() {

    @Inject lateinit var overlayWindowManager: OverlayWindowManager
    @Inject lateinit var auditLogger: AuditLogger
    @Inject lateinit var agentRuntime: AgentRuntime
    @Inject lateinit var projectionManager: ChibiProjectionManager
    @Inject lateinit var systemEventReceiver: SystemEventReceiver
    @Inject lateinit var notificationEventBridge: NotificationEventBridge
    @Inject lateinit var initiativeEngine: InitiativeEngine
    @Inject lateinit var memoryWorkScheduler: MemoryWorkScheduler
    @Inject lateinit var miniCpmvInference: MiniCPMVInference
    @Inject lateinit var networkObserver: NetworkObserver
    @Inject lateinit var appLaunchDetector: AppLaunchDetector
    @Inject lateinit var calendarEventObserver: CalendarEventObserver

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): ChibiService = this@ChibiService
    }

    override fun onCreate() {
        super.onCreate()
        Timber.i("ChibiService.onCreate()")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("ChibiService.onStartCommand() action=${intent?.action}")

        startForegroundWithType()
        overlayWindowManager.showBubble()
        agentRuntime.start()

        // Phase 5: recreate MediaProjection dari saved token kalau ada.
        if (projectionManager.hasToken()) {
            val ok = projectionManager.tryRecreate()
            Timber.i("MediaProjection recreate on service start: $ok")
        }

        // Phase 6 + 9: event sources + observers + InitiativeEngine.
        systemEventReceiver.register()
        notificationEventBridge.start()
        networkObserver.start()
        appLaunchDetector.start()
        calendarEventObserver.start()
        initiativeEngine.start()

        // Phase 7: schedule periodic memory workers (pattern miner + decay + cleanup).
        memoryWorkScheduler.schedule()

        auditLogger.log(
            actionType = AuditActionType.SERVICE_STARTED,
            dataSummary = "ChibiService foreground started, AgentRuntime + InitiativeEngine active",
        )

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Timber.i("ChibiService.onDestroy()")
        initiativeEngine.stop()
        calendarEventObserver.stop()
        appLaunchDetector.stop()
        networkObserver.stop()
        notificationEventBridge.stop()
        systemEventReceiver.unregister()
        agentRuntime.stop()
        overlayWindowManager.hideBubble()
        projectionManager.teardown()
        // Phase 9: shutdown JNI handle MiniCPM-V (was leaking pre-Phase 9).
        miniCpmvInference.shutdown()
        scope.cancel()
        auditLogger.log(
            actionType = AuditActionType.SERVICE_STOPPED,
            dataSummary = "ChibiService destroyed, AgentRuntime + InitiativeEngine stopped",
        )
        super.onDestroy()
    }

    private fun startForegroundWithType() {
        val notification = buildFgsNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ wajib spec foregroundServiceType di runtime.
            // Bitmask: microphone (voice) | specialUse (agent backend) | mediaProjection (vision screen capture).
            // MEDIA_PROJECTION hanya kalau token tersedia — Android 15+ strict: tipe tidak boleh
            // di-claim tanpa active session. Selalu include kalau token ready supaya foreground
            // promotion lulus saat first capture.
            var typeMask = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (projectionManager.hasToken()) {
                typeMask = typeMask or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            startForeground(NOTIFICATION_ID, notification, typeMask)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildFgsNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.fgs_notification_title))
            .setContentText(getString(R.string.fgs_notification_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pending)
            .setShowWhen(false)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.fgs_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.fgs_notification_channel_description)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_CHANNEL_ID = "chibiclaw_agent"

        fun start(context: android.content.Context) {
            val intent = Intent(context, ChibiService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, ChibiService::class.java))
        }
    }
}
