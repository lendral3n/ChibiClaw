package com.chibiclaw.world.observers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.chibiclaw.agent.initiative.EventBus
import com.chibiclaw.agent.initiative.TriggerEvent
import com.chibiclaw.agent.initiative.trigger.EventType
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SystemEventReceiver — register BroadcastReceiver internal (di-app) untuk
 * BATTERY_LOW / BATTERY_OKAY / SCREEN_ON / SCREEN_OFF / USER_PRESENT /
 * BOOT_COMPLETED / CONNECTIVITY_CHANGE / POWER_CONNECTED / POWER_DISCONNECTED.
 *
 * Emit ke EventBus → InitiativeEngine consume.
 *
 * Lifecycle: registered di ChibiService.onCreate; unregistered di onDestroy.
 */
@Singleton
class SystemEventReceiver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventBus: EventBus,
) {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val ev = when (action) {
                Intent.ACTION_BATTERY_LOW -> {
                    val level = batteryLevel(intent)
                    TriggerEvent(EventType.BATTERY_LOW, mapOf("value" to level.toString()))
                }
                Intent.ACTION_POWER_CONNECTED -> TriggerEvent(EventType.BATTERY_CHARGING)
                Intent.ACTION_POWER_DISCONNECTED -> TriggerEvent(EventType.BATTERY_UNPLUGGED)
                Intent.ACTION_SCREEN_ON -> TriggerEvent(EventType.SCREEN_ON)
                Intent.ACTION_SCREEN_OFF -> TriggerEvent(EventType.SCREEN_OFF)
                Intent.ACTION_USER_PRESENT -> TriggerEvent(EventType.USER_PRESENT)
                Intent.ACTION_BOOT_COMPLETED -> TriggerEvent(EventType.BOOT_COMPLETED)
                else -> null
            }
            ev?.let {
                eventBus.emit(it)
                Timber.v("Emitted ${it.type}")
            }
        }
    }

    private var registered = false

    @Synchronized
    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // Android 14+: receiver yang dynamic register harus tag RECEIVER_EXPORTED atau RECEIVER_NOT_EXPORTED.
        // Semua action di atas adalah system-broadcast — pakai NOT_EXPORTED supaya tidak terima broadcast pihak ke-3.
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        registered = true
        Timber.i("SystemEventReceiver registered")
    }

    @Synchronized
    fun unregister() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
        Timber.i("SystemEventReceiver unregistered")
    }

    private fun batteryLevel(intent: Intent): Int {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level < 0) -1 else (level * 100 / scale)
    }
}
