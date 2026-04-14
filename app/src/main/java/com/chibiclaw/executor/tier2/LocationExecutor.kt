package com.chibiclaw.executor.tier2

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import com.chibiclaw.executor.tier4.DeepSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Phase 11 — Location: GPS toggle, current location, status.
 */
@Singleton
class LocationExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deepSettings: DeepSettings
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    suspend fun perform(operation: String, value: String): String {
        val op = operation.trim().lowercase()
        return try {
            when (op) {
                "status", "get", "gps_status" -> getLocationStatus()
                "current", "now", "lokasi", "dimana" -> getCurrentLocation()
                "gps_on", "on" -> {
                    deepSettings.putRaw("secure", "location_providers_allowed", "+gps")
                }
                "gps_off", "off" -> {
                    deepSettings.putRaw("secure", "location_providers_allowed", "-gps")
                }
                "high_accuracy" -> {
                    deepSettings.putRaw("secure", "location_mode", "3")
                }
                "battery_saving" -> {
                    deepSettings.putRaw("secure", "location_mode", "2")
                }
                "device_only" -> {
                    deepSettings.putRaw("secure", "location_mode", "1")
                }
                else -> "location_error: unknown operation '$op'"
            }
        } catch (e: SecurityException) {
            "location_error: permission denied — ${e.message}"
        } catch (e: Exception) {
            "location_error: ${e.message}"
        }
    }

    private fun getLocationStatus(): String {
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        val providers = locationManager.getProviders(true)
        return "Location: GPS=${if (gpsEnabled) "ON" else "OFF"} | " +
            "Network=${if (networkEnabled) "ON" else "OFF"} | " +
            "Active providers: ${providers.joinToString(",")}"
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): String {
        // Try getting last known location first (fastest)
        val lastKnown = try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (_: SecurityException) {
            return "location_error: ACCESS_FINE_LOCATION permission not granted"
        }

        if (lastKnown != null) {
            val lat = lastKnown.latitude
            val lon = lastKnown.longitude
            val acc = lastKnown.accuracy
            val address = reverseGeocode(lat, lon)
            return "Location: ${String.format("%.6f", lat)}, ${String.format("%.6f", lon)} | " +
                "Accuracy: ${String.format("%.0f", acc)}m | " +
                "Address: $address | " +
                "Provider: ${lastKnown.provider}"
        }

        return "location: no recent location available. Enable GPS and try again."
    }

    private fun reverseGeocode(lat: Double, lon: Double): String {
        return try {
            if (Geocoder.isPresent()) {
                @Suppress("DEPRECATION")
                val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    listOfNotNull(
                        addr.thoroughfare,
                        addr.subLocality,
                        addr.locality,
                        addr.adminArea,
                        addr.countryName
                    ).joinToString(", ")
                } else "unknown"
            } else "geocoder not available"
        } catch (e: Exception) {
            "geocode_error"
        }
    }
}
