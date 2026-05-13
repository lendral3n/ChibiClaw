package com.chibiclaw.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.chibiclaw.accessibility.a11y.A11yTreeWalker
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * ChibiAccessibilityService — Phase 3.
 *
 * Tujuan utama: jadi gateway untuk a11y_* tools (click, type, describe, scroll).
 * Service ini di-bind oleh Android system saat user enable di Settings →
 * Accessibility. Singleton reference di-expose via [instance] supaya
 * ToolDispatcher bisa akses dari mana saja.
 *
 * Phase 6 akan ditambah event-driven listener untuk NOTIFICATION /
 * APP_LAUNCH triggers (sekarang minimal: cuma log untuk debug).
 */
class ChibiAccessibilityService : AccessibilityService() {

    val treeWalker by lazy { A11yTreeWalker(this) }

    override fun onCreate() {
        super.onCreate()
        Timber.i("ChibiAccessibilityService.onCreate()")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ref = WeakReference(this)
        Timber.i("ChibiAccessibilityService connected — a11y tools available")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Phase 3: minimal — log foreground app changes. Phase 6 akan emit
        // event ke InitiativeEngine untuk standing instructions (APP_LAUNCH).
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (pkg != null && pkg != "com.chibiclaw") {
                Timber.v("a11y window changed: $pkg")
            }
        }
    }

    override fun onInterrupt() {
        Timber.w("ChibiAccessibilityService interrupted")
    }

    override fun onDestroy() {
        Timber.i("ChibiAccessibilityService.onDestroy()")
        ref = WeakReference(null)
        super.onDestroy()
    }

    companion object {
        @Volatile private var ref: WeakReference<ChibiAccessibilityService> = WeakReference(null)
        fun instance(): ChibiAccessibilityService? = ref.get()
        fun isConnected(): Boolean = ref.get() != null
    }
}
