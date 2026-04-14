package com.chibiclaw.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.chibiclaw.executor.KillSwitch
import com.chibiclaw.state.ChibiState
import com.chibiclaw.state.ChibiStateMachine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloatingOverlay @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateMachine: ChibiStateMachine,
    private val killSwitch: KillSwitch
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var bubbleView: View? = null
    private var panelView: View? = null
    private var observerJob: Job? = null
    private var isPanelVisible = false

    fun start() {
        observerJob = scope.launch {
            stateMachine.state.collect { state ->
                when (state) {
                    ChibiState.IDLE, ChibiState.COMPLETED -> hide()
                    else -> show(state)
                }
            }
        }
    }

    fun stop() {
        observerJob?.cancel()
        hide()
    }

    private fun show(state: ChibiState) {
        mainHandler.post {
            if (bubbleView == null) {
                createBubble()
            }
            updateBubbleColor(state)
        }
    }

    private fun hide() {
        mainHandler.post {
            dismissPanel()
            bubbleView?.let {
                try { windowManager.removeView(it) } catch (_: Exception) {}
            }
            bubbleView = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createBubble() {
        val bubble = TextView(context).apply {
            text = "●"
            textSize = 24f
            setTextColor(android.graphics.Color.parseColor("#FF7C4DFF"))
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.parseColor("#CC1E1E2E"))
            elevation = 8f
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            x = 0
            y = 0
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var longPressJob: Job? = null

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    longPressJob = scope.launch {
                        kotlinx.coroutines.delay(800)
                        if (!isDragging) {
                            killSwitch.activate()
                            hide()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        isDragging = true
                        longPressJob?.cancel()
                    }
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try { windowManager.updateViewLayout(bubble, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressJob?.cancel()
                    if (!isDragging) {
                        togglePanel()
                    }
                    true
                }
                else -> false
            }
        }

        bubbleView = bubble
        try {
            windowManager.addView(bubble, params)
        } catch (_: Exception) {}
    }

    private fun updateBubbleColor(state: ChibiState) {
        val colorHex = when (state) {
            ChibiState.PLANNING -> "#FF448AFF"
            ChibiState.EXECUTING -> "#FF00C853"
            ChibiState.ERROR_RECOVERY -> "#FFFF5252"
            ChibiState.WAITING_USER -> "#FFFFD740"
            ChibiState.PAUSED -> "#FF9E9E9E"
            else -> "#FF757575"
        }
        (bubbleView as? TextView)?.setTextColor(android.graphics.Color.parseColor(colorHex))
    }

    private fun togglePanel() {
        if (isPanelVisible) dismissPanel() else showPanel()
    }

    private fun showPanel() {
        if (panelView != null) return

        val stateLabel = when (stateMachine.current) {
            ChibiState.PLANNING -> "Merencanakan..."
            ChibiState.EXECUTING -> "Menjalankan perintah..."
            ChibiState.ERROR_RECOVERY -> "Error recovery"
            ChibiState.WAITING_USER -> "Menunggu konfirmasi"
            ChibiState.PAUSED -> "Dijeda"
            else -> "Aktif"
        }

        val panel = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(android.graphics.Color.parseColor("#EE1E1E2E"))
        }

        val stateText = TextView(context).apply {
            text = stateLabel
            setTextColor(android.graphics.Color.parseColor("#FFE8E8F0"))
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }

        val stopBtn = android.widget.Button(context).apply {
            text = "■ STOP"
            setBackgroundColor(android.graphics.Color.parseColor("#FFFF5252"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener {
                killSwitch.activate()
                dismissPanel()
                hide()
            }
        }

        panel.addView(stateText)
        panel.addView(stopBtn)

        val params = WindowManager.LayoutParams(
            400,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            x = 80
        }

        panelView = panel
        isPanelVisible = true
        try {
            windowManager.addView(panel, params)
        } catch (_: Exception) {
            panelView = null
            isPanelVisible = false
        }
    }

    private fun dismissPanel() {
        panelView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        panelView = null
        isPanelVisible = false
    }
}
