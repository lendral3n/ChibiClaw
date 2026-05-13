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
import com.chibiclaw.compliance.AuditLogger
import com.chibiclaw.data.database.AuditActionType
import com.chibiclaw.service.overlay.OverlayWindowManager
import com.chibiclaw.ui.MainActivity
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
        auditLogger.log(
            actionType = AuditActionType.SERVICE_STARTED,
            dataSummary = "ChibiService foreground started",
        )

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Timber.i("ChibiService.onDestroy()")
        overlayWindowManager.hideBubble()
        scope.cancel()
        auditLogger.log(
            actionType = AuditActionType.SERVICE_STOPPED,
            dataSummary = "ChibiService destroyed",
        )
        super.onDestroy()
    }

    private fun startForegroundWithType() {
        val notification = buildFgsNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ wajib spec foregroundServiceType di runtime
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
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
