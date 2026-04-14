package com.chibiclaw.executor.tier3

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.chibiclaw.executor.GestureAction
import com.chibiclaw.service.ChibiAccessibility
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Phase 1.1 — low-level gesture dispatcher.
 *
 * Wraps [AccessibilityService.dispatchGesture] with a coroutine-friendly
 * API and a small command set that Gemma can reason about:
 *   tap_coord, long_press, double_tap, swipe, drag, pinch_in, pinch_out
 *
 * Why this exists: [AccessibilityExecutor] can only click nodes it finds in
 * the accessibility tree. Games, Canvas-backed UIs, WebViews and custom
 * drawings are invisible to that tree — the tree reports ONE giant
 * SurfaceView and that's it. The only way to interact is to dispatch raw
 * touch events at a pixel coordinate, which is exactly what
 * [dispatchGesture] is designed for. Gemma gets those coordinates from
 * `vision_analyze(find_element, ...)`.
 *
 * Everything is suspending so [ExecutionRouter] can wait for the gesture
 * to actually finish before moving on to the post-action observation step.
 */
@Singleton
class GestureDispatcher @Inject constructor() {

    suspend fun perform(action: GestureAction): String {
        val svc = ChibiAccessibility.getInstance()
            ?: return "gesture_error: accessibility service belum aktif"

        val description = try {
            buildGesture(action) ?: return "gesture_error: invalid kind=${action.kind}"
        } catch (e: IllegalArgumentException) {
            return "gesture_error: ${e.message}"
        }

        return dispatchAndAwait(svc, description, action.kind)
    }

    private fun buildGesture(action: GestureAction): GestureDescription? {
        val x1 = action.x1.toFloat().coerceAtLeast(1f)
        val y1 = action.y1.toFloat().coerceAtLeast(1f)
        val x2 = action.x2.toFloat()
        val y2 = action.y2.toFloat()

        return when (action.kind.lowercase()) {
            "tap_coord", "tap" -> {
                val duration = if (action.durationMs > 0) action.durationMs.toLong() else 60L
                val path = Path().apply { moveTo(x1, y1); lineTo(x1, y1) }
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                    .build()
            }
            "long_press" -> {
                val duration = if (action.durationMs > 0) action.durationMs.toLong() else 800L
                val path = Path().apply { moveTo(x1, y1); lineTo(x1, y1) }
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                    .build()
            }
            "double_tap" -> {
                val tapDur = 60L
                val gap = 80L
                val path1 = Path().apply { moveTo(x1, y1); lineTo(x1, y1) }
                val path2 = Path().apply { moveTo(x1, y1); lineTo(x1, y1) }
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path1, 0, tapDur))
                    .addStroke(GestureDescription.StrokeDescription(path2, tapDur + gap, tapDur))
                    .build()
            }
            "swipe" -> {
                require(x2 > 0f && y2 > 0f) { "swipe requires x2,y2" }
                val duration = if (action.durationMs > 0) action.durationMs.toLong() else 300L
                val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                    .build()
            }
            "drag" -> {
                require(x2 > 0f && y2 > 0f) { "drag requires x2,y2" }
                // Drag = longer, slower stroke so the target recognises it
                // as a drag rather than a fling. Gemma can override via durationMs.
                val duration = if (action.durationMs > 0) action.durationMs.toLong() else 800L
                val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                    .build()
            }
            "pinch_in" -> {
                // Two fingers start apart, come together at (x1, y1)
                val duration = if (action.durationMs > 0) action.durationMs.toLong() else 400L
                val spread = if (x2 > 0f) x2 else 300f
                val left = Path().apply { moveTo(x1 - spread, y1); lineTo(x1 - 20f, y1) }
                val right = Path().apply { moveTo(x1 + spread, y1); lineTo(x1 + 20f, y1) }
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(left, 0, duration))
                    .addStroke(GestureDescription.StrokeDescription(right, 0, duration))
                    .build()
            }
            "pinch_out" -> {
                val duration = if (action.durationMs > 0) action.durationMs.toLong() else 400L
                val spread = if (x2 > 0f) x2 else 300f
                val left = Path().apply { moveTo(x1 - 20f, y1); lineTo(x1 - spread, y1) }
                val right = Path().apply { moveTo(x1 + 20f, y1); lineTo(x1 + spread, y1) }
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(left, 0, duration))
                    .addStroke(GestureDescription.StrokeDescription(right, 0, duration))
                    .build()
            }
            else -> null
        }
    }

    private suspend fun dispatchAndAwait(
        svc: AccessibilityService,
        description: GestureDescription,
        kind: String
    ): String = suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (cont.isActive) cont.resume("gesture_success: $kind")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (cont.isActive) cont.resume("gesture_cancelled: $kind")
            }
        }
        val dispatched = try {
            svc.dispatchGesture(description, callback, handler)
        } catch (e: SecurityException) {
            Log.w(TAG, "dispatchGesture denied: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "dispatchGesture threw: ${e.message}")
            false
        }
        if (!dispatched && cont.isActive) {
            cont.resume("gesture_rejected: dispatchGesture returned false")
        }
    }

    companion object {
        private const val TAG = "GestureDispatcher"
    }
}
