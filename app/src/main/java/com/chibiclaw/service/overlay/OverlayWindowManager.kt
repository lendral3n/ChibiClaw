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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.chibiclaw.ui.theme.ChibiClawTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Manage overlay window di atas semua app (SYSTEM_ALERT_WINDOW).
 *
 * Phase 0: bubble collapsed dengan drag + snap-to-edge.
 * Phase 1+: tap to expand chat panel, status color per ChibiState.
 *
 * Window flags:
 * - TYPE_APPLICATION_OVERLAY: standard untuk Android 8+
 * - FLAG_NOT_FOCUSABLE: tidak rebut keyboard focus dari app di bawah
 * - FLAG_NOT_TOUCH_MODAL: touch outside bubble pass-through ke app
 * - FLAG_LAYOUT_NO_LIMITS: bubble bisa di edge
 */
@Singleton
class OverlayWindowManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val windowManager: WindowManager,
) {

    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

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
        if (composeView != null) {
            Timber.d("Bubble sudah visible, skip")
            return
        }

        val owner = OverlayLifecycleOwner().apply { onCreate() }
        lifecycleOwner = owner

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
        layoutParams = params

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
        composeView = view

        attachDragHandler(view, params)

        windowManager.addView(view, params)
        owner.onStart()
        owner.onResume()
        Timber.i("Overlay bubble shown")
    }

    fun hideBubble() {
        val view = composeView ?: return
        val owner = lifecycleOwner

        runCatching { windowManager.removeView(view) }
            .onFailure { Timber.w(it, "removeView failed (already removed?)") }

        owner?.onPause()
        owner?.onStop()
        owner?.onDestroy()

        composeView = null
        lifecycleOwner = null
        layoutParams = null
        Timber.i("Overlay bubble hidden")
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragHandler(view: View, params: WindowManager.LayoutParams) {
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
                    params.x = (startX + dx).coerceIn(0, screenWidth() - v.width)
                    params.y = (startY + dy).coerceIn(0, screenHeight() - v.height)
                    runCatching { windowManager.updateViewLayout(v, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        // Tap — Phase 1+ akan expand chat panel
                        Timber.d("Bubble tapped (Phase 1+: expand panel)")
                    } else {
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
    }
}
