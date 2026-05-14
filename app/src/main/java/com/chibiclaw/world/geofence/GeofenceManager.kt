package com.chibiclaw.world.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.chibiclaw.agent.initiative.EventBus
import com.chibiclaw.agent.initiative.TriggerEvent
import com.chibiclaw.agent.initiative.trigger.EventType
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GeofenceManager — wrap GeofencingClient untuk register places per
 * memory_key dengan radius default 200m.
 *
 * Caller (Phase 6 polish atau via tool `geofence_register`): panggil
 * `addGeofence(key, lat, lng, radius)` saat user create memory FACT
 * "office_location" dll.
 *
 * Phase 9 polish: dedicated `tool_geofence_register` supaya LLM bisa
 * register placeholder kalau standing instruction butuh.
 *
 * Hasil ENTER/EXIT event di-route via [GeofenceBroadcastReceiver] →
 * EventBus.
 */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    val eventBus: EventBus,
) {

    private val client: GeofencingClient by lazy { LocationServices.getGeofencingClient(context) }

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = ACTION_GEOFENCE_EVENT
        }
        PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")  // checked via hasPermission()
    fun addGeofence(
        placeKey: String,
        lat: Double,
        lng: Double,
        radiusMeters: Float = 200f,
    ): Boolean {
        if (!hasPermission()) {
            Timber.w("Geofence add skipped — permission absent")
            return false
        }
        val geofence = Geofence.Builder()
            .setRequestId(placeKey)
            .setCircularRegion(lat, lng, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT,
            )
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()
        return runCatching {
            client.addGeofences(request, pendingIntent)
            Timber.i("Geofence registered: $placeKey @($lat,$lng) ${radiusMeters}m")
            true
        }.onFailure { Timber.w(it, "Geofence add failed for $placeKey") }.getOrDefault(false)
    }

    fun removeGeofence(placeKey: String) {
        runCatching { client.removeGeofences(listOf(placeKey)) }
    }

    companion object {
        const val ACTION_GEOFENCE_EVENT = "com.chibiclaw.GEOFENCE_EVENT"
        const val EXTRA_PLACE_KEY = "place_key"

        fun emitTransition(eventBus: EventBus, placeKey: String, enter: Boolean) {
            eventBus.emit(
                TriggerEvent(
                    type = if (enter) EventType.GEOFENCE_ENTER else EventType.GEOFENCE_EXIT,
                    metadata = mapOf("place_key" to placeKey),
                ),
            )
        }
    }
}
