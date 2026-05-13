package com.chibiclaw.service.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.chibiclaw.agent.ConversationManager
import com.chibiclaw.data.repository.TaskRepository
import com.chibiclaw.ui.theme.ChibiClawTheme
import com.chibiclaw.voice.VoicePipelineOrchestrator
import com.chibiclaw.voice.tts.ElevenLabsTts
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Manage overlay window. Phase 1: dua mode — collapsed bubble + expanded chat panel.
 *
 * Tap bubble → toggle expanded state. Saat expanded, panel chat ~360x520dp
 * muncul di samping bubble; tap "×" → kembali ke bubble.
 *
 * Drag: cuma di mode bubble (collapsed). Saat expanded, panel fixed position.
 */
@Singleton
class OverlayWindowManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val windowManager: WindowManager,
    private val conversationManager: ConversationManager,
    private val taskRepository: TaskRepository,
    private val voicePipeline: VoicePipelineOrchestrator,
    private val elevenLabsTts: ElevenLabsTts,
) {

    private var bubbleLifecycleOwner: OverlayLifecycleOwner? = null
    private var bubbleView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var panelLifecycleOwner: OverlayLifecycleOwner? = null
    private var panelView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var expanded: Boolean = false

    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    @SuppressLint("RtlHardcoded")
    fun showBubble() {
        if (!canDrawOverlays()) {
            Timber.w("Overlay permission belum di-grant; bubble tidak ditampilkan")
            return
        }
        if (bubbleView != null) return

        val owner = OverlayLifecycleOwner().apply { onCreate() }
        bubbleLifecycleOwner = owner

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = INITIAL_X
            y = INITIAL_Y
        }
        bubbleParams = params

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ChibiClawTheme(useDarkTheme = false) {
                    BubbleOverlay()
                }
            }
        }
        bubbleView = view

        attachBubbleTouchHandler(view, params)
        windowManager.addView(view, params)

        owner.onStart()
        owner.onResume()
        Timber.i("Overlay bubble shown at (${params.x},${params.y})")
    }

    fun hideBubble() {
        if (expanded) collapsePanel()
        val view = bubbleView ?: return
        val owner = bubbleLifecycleOwner

        runCatching { windowManager.removeView(view) }
            .onFailure { Timber.w(it, "removeView failed (already removed?)") }

        owner?.onPause()
        owner?.onStop()
        owner?.onDestroy()

        bubbleView = null
        bubbleLifecycleOwner = null
        bubbleParams = null
        Timber.i("Overlay bubble hidden")
    }

    /** Tap bubble → toggle expanded chat panel. */
    private fun togglePanel() {
        if (expanded) {
            collapsePanel()
        } else {
            expandPanel()
        }
    }

    @SuppressLint("RtlHardcoded")
    private fun expandPanel() {
        if (panelView != null) return
        val bubble = bubbleParams ?: return

        val owner = OverlayLifecycleOwner().apply { onCreate() }
        panelLifecycleOwner = owner

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            overlayType(),
            // Panel needs to be focusable supaya keyboard input bisa muncul
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            val screenW = screenWidth()
            x = if (bubble.x < screenW / 2) {
                bubble.x + BUBBLE_SIZE_PX
            } else {
                (bubble.x - PANEL_WIDTH_PX).coerceAtLeast(0)
            }
            y = bubble.y.coerceAtMost(screenHeight() - PANEL_HEIGHT_PX)
        }
        panelParams = params

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ChibiClawTheme(useDarkTheme = false) {
                    OverlayChatPanel(
                        conversationManager = conversationManager,
                        taskRepository = taskRepository,
                        voicePipeline = voicePipeline,
                        elevenLabsTts = elevenLabsTts,
                        onClose = { togglePanel() },
                    )
                }
            }
        }
        panelView = view

        windowManager.addView(view, params)
        owner.onStart()
        owner.onResume()
        expanded = true
        Timber.i("Overlay panel expanded")
    }

    private fun collapsePanel() {
        val view = panelView ?: return
        val owner = panelLifecycleOwner

        runCatching { windowManager.removeView(view) }
            .onFailure { Timber.w(it, "panel removeView failed") }

        owner?.onPause()
        owner?.onStop()
        owner?.onDestroy()

        panelView = null
        panelLifecycleOwner = null
        panelParams = null
        expanded = false
        Timber.i("Overlay panel collapsed")
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachBubbleTouchHandler(view: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var startRawX = 0f
        var startRawY = 0f
        var moved = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    startRawX = event.rawX
                    startRawY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startRawX).toInt()
                    val dy = (event.rawY - startRawY).toInt()
                    if (abs(dx) > TOUCH_SLOP || abs(dy) > TOUCH_SLOP) moved = true
                    if (!expanded) {
                        params.x = (startX + dx).coerceIn(0, screenWidth() - v.width)
                        params.y = (startY + dy).coerceIn(0, screenHeight() - v.height)
                        runCatching { windowManager.updateViewLayout(v, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        togglePanel()
                    } else if (!expanded) {
                        snapToEdge(v, params)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge(view: View, params: WindowManager.LayoutParams) {
        val screen = screenWidth()
        val halfwayX = (screen - view.width) / 2
        params.x = if (params.x < halfwayX) 0 else (screen - view.width)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun screenWidth(): Int = context.resources.displayMetrics.widthPixels
    private fun screenHeight(): Int = context.resources.displayMetrics.heightPixels

    private fun overlayType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    companion object {
        private const val INITIAL_X = 60
        private const val INITIAL_Y = 240
        private const val TOUCH_SLOP = 10

        private const val BUBBLE_SIZE_PX = 180   // 56dp ≈ 180px @ ~3x density
        private const val PANEL_WIDTH_PX = 1100  // 360dp ≈ 1100px
        private const val PANEL_HEIGHT_PX = 1600 // 520dp ≈ 1600px
    }
}
