package com.chibiclaw.world.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chibiclaw.agent.initiative.EventBus
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber

/**
 * GeofenceBroadcastReceiver — system-broadcast receiver untuk geofence
 * transitions. Pakai EntryPoint Hilt karena BroadcastReceiver
 * instantiated by Android system, bukan Hilt.
 *
 * Manifest declaration required.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BusEntryPoint {
        fun eventBus(): EventBus
    }

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Timber.w("Geofence event error code=${event.errorCode}")
            return
        }
        val bus = EntryPointAccessors.fromApplication(
            context.applicationContext, BusEntryPoint::class.java,
        ).eventBus()

        val enter = event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER
        event.triggeringGeofences.orEmpty().forEach { gf ->
            GeofenceManager.emitTransition(bus, gf.requestId, enter)
            Timber.i("Geofence ${if (enter) "ENTER" else "EXIT"}: ${gf.requestId}")
        }
    }
}
