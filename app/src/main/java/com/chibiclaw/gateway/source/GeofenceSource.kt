package com.chibiclaw.gateway.source

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.chibiclaw.gateway.CommandGateway
import com.chibiclaw.gateway.CommandSource
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 7.2 — Geofence trigger source.
 *
 * Users can say *"kalau aku sampai rumah, nyalakan wifi"* and we
 * translate that into a circular Geofence (Play Services Location)
 * that, when entered, dispatches a `CommandRequest` into
 * [CommandGateway]. The mapping between a geofence id and its
 * canonical Chibi command is kept in a small in-memory table which
 * the caller (skills or the UI) populates at registration time.
 *
 * We deliberately do NOT persist geofences to disk; the expectation
 * is that each skill re-registers its geofences on service boot via
 * [restoreAll]. This keeps the crash path clean — a stale Play
 * Services geofence that points at a dead command id just becomes
 * a no-op.
 */
@Singleton
class GeofenceSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val commandGateway: CommandGateway
) {
    private val client: GeofencingClient = LocationServices.getGeofencingClient(context)
    private val commandMap = ConcurrentHashMap<String, String>() // geofenceId → command
    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(ACTION_GEOFENCE_FIRED).apply { setPackage(context.packageName) }
        PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    fun isAvailable(): Boolean = hasLocationPermission()

    /**
     * Register a geofence centred at [lat]/[lng] with a radius in
     * metres. When the user enters the circle, [command] is pushed
     * to the gateway with LOW priority.
     */
    fun register(
        id: String,
        lat: Double,
        lng: Double,
        radiusMeters: Float,
        command: String,
        transitions: Int = Geofence.GEOFENCE_TRANSITION_ENTER
    ): Boolean {
        if (!hasLocationPermission()) {
            Log.w(TAG, "register($id) denied — missing ACCESS_FINE_LOCATION")
            return false
        }
        val fence = Geofence.Builder()
            .setRequestId(id)
            .setCircularRegion(lat, lng, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(transitions)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(fence)
            .build()
        commandMap[id] = command
        return try {
            client.addGeofences(request, pendingIntent).addOnFailureListener {
                Log.e(TAG, "addGeofences($id) failed: ${it.message}")
                commandMap.remove(id)
            }
            true
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException registering geofence $id: ${se.message}")
            commandMap.remove(id)
            false
        }
    }

    fun unregister(id: String) {
        commandMap.remove(id)
        try {
            client.removeGeofences(listOf(id))
        } catch (_: Exception) { }
    }

    fun unregisterAll() {
        commandMap.clear()
        try {
            client.removeGeofences(pendingIntent)
        } catch (_: Exception) { }
    }

    /**
     * Called by [GeofenceReceiver] — this is the entry point for a
     * fired geofence. It looks up the command for the geofence id
     * and enqueues it.
     */
    internal fun onFired(geofenceIds: List<String>) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            for (id in geofenceIds) {
                val command = commandMap[id] ?: continue
                Log.d(TAG, "Geofence fired: $id → $command")
                commandGateway.submitDirect(command, CommandSource.CRON)
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bg = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            return bg
        }
        return true
    }

    companion object {
        const val ACTION_GEOFENCE_FIRED = "com.chibiclaw.GEOFENCE_FIRED"
        private const val TAG = "GeofenceSource"
    }
}

/**
 * Static BroadcastReceiver that converts a raw Play Services
 * GeofencingEvent into a call on [GeofenceSource.onFired]. We use a
 * receiver rather than a service because the callback payload is
 * tiny and a coroutine dispatch to the gateway is cheap.
 *
 * The receiver is registered at runtime inside [ChibiService] — it
 * isn't in the manifest because that would require exporting and
 * picking a static Hilt entry point, and the runtime path is safer.
 */
@AndroidEntryPoint
class GeofenceReceiver : BroadcastReceiver() {
    @Inject lateinit var geofenceSource: GeofenceSource

    override fun onReceive(context: Context?, intent: Intent?) {
        intent ?: return
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.e(TAG, "Geofence event error: ${event.errorCode}")
            return
        }
        val ids = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        if (ids.isEmpty()) return
        geofenceSource.onFired(ids)
    }

    companion object {
        private const val TAG = "GeofenceReceiver"

        fun makeFilter(): IntentFilter =
            IntentFilter(GeofenceSource.ACTION_GEOFENCE_FIRED)
    }
}
