package com.chibiclaw.safety

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class ConfirmationOverlay @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Shows a confirmation overlay to the user.
     * Suspends until the user answers Yes/No or until timeout (30s → auto-deny).
     *
     * @return true if user confirmed, false if denied or timed out
     */
    suspend fun requestConfirmation(
        action: String,
        severity: Severity,
        targetApp: String = ""
    ): Boolean {
        return withTimeoutOrNull(30_000L) {
            suspendCancellableCoroutine { continuation ->
                mainHandler.post {
                    showOverlay(action, severity, targetApp) { confirmed ->
                        if (continuation.isActive) continuation.resume(confirmed)
                    }
                }
                continuation.invokeOnCancellation {
                    mainHandler.post { dismissOverlay() }
                }
            }
        } ?: false // timeout → auto-deny
    }

    private var currentView: android.view.View? = null

    private fun showOverlay(
        action: String,
        severity: Severity,
        targetApp: String,
        onResult: (Boolean) -> Unit
    ) {
        dismissOverlay()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }

        // Build view programmatically (avoids XML dependency)
        val view = buildConfirmationView(action, severity, targetApp,
            onYes = {
                dismissOverlay()
                onResult(true)
            },
            onNo = {
                dismissOverlay()
                onResult(false)
            }
        )

        currentView = view
        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            // SYSTEM_ALERT_WINDOW permission not granted
            onResult(false)
        }
    }

    private fun dismissOverlay() {
        currentView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        currentView = null
    }

    private fun buildConfirmationView(
        action: String,
        severity: Severity,
        targetApp: String,
        onYes: () -> Unit,
        onNo: () -> Unit
    ): android.view.View {
        val ctx = context

        // Build layout programmatically
        val root = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(android.graphics.Color.parseColor("#CC141420"))
            clipToOutline = true
        }

        val severityColor = when (severity) {
            Severity.HIGH -> android.graphics.Color.parseColor("#FFFF6D00")
            Severity.MEDIUM -> android.graphics.Color.parseColor("#FFFFD740")
            else -> android.graphics.Color.parseColor("#FF69F0AE")
        }

        val severityBadge = TextView(ctx).apply {
            text = "⚠ ${severity.name} SEVERITY"
            setTextColor(severityColor)
            textSize = 12f
        }

        val titleText = TextView(ctx).apply {
            text = "Konfirmasi Aksi"
            setTextColor(android.graphics.Color.parseColor("#FFE8E8F0"))
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 8)
        }

        val actionText = TextView(ctx).apply {
            text = action
            setTextColor(android.graphics.Color.parseColor("#FFD0D0E0"))
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }

        val appText = if (targetApp.isNotEmpty()) TextView(ctx).apply {
            text = "Target: $targetApp"
            setTextColor(android.graphics.Color.parseColor("#FF9090A8"))
            textSize = 12f
            setPadding(0, 0, 0, 16)
        } else null

        val buttonRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            weightSum = 2f
        }

        val noBtn = Button(ctx).apply {
            text = "Tidak"
            setOnClickListener { onNo() }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginEnd = 8 }
        }

        val yesBtn = Button(ctx).apply {
            text = "Ya, Lanjut"
            setBackgroundColor(android.graphics.Color.parseColor("#FF7C4DFF"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { onYes() }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = 8 }
        }

        buttonRow.addView(noBtn)
        buttonRow.addView(yesBtn)

        root.addView(severityBadge)
        root.addView(titleText)
        root.addView(actionText)
        appText?.let { root.addView(it) }
        root.addView(buttonRow)

        return root
    }
}
