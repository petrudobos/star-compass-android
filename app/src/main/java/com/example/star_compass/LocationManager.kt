package com.example.star_compass

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.example.star_compass.ui.AppError
import com.example.star_compass.ui.ErrorTracker
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationManager(context: Context, private val errorTracker: ErrorTracker? = null) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    private val _location = MutableStateFlow<Location?>(null)
    val location = _location.asStateFlow()

    // Update every 5 minutes (300_000 ms), minimum every 10 minutes.
    // PRIORITY_BALANCED_POWER_ACCURACY uses cell/WiFi, not GPS — battery-friendly.
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        300_000L
    )
        .setMinUpdateIntervalMillis(600_000L)
        .setMaxUpdateDelayMillis(600_000L)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { _location.value = it }
        }
    }

    @SuppressLint("MissingPermission")
    fun requestSingleUpdate() {
        // Get a fast last-known fix immediately on startup
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc -> if (loc != null) _location.value = loc }
                .addOnFailureListener { e ->
                    errorTracker?.reportError(AppError.LocationError(e.message ?: "Unknown location error"))
                }
        } catch (e: Exception) {
            errorTracker?.reportError(AppError.LocationError(e.message ?: "Security exception"))
        }

        // Then start periodic updates (battery-friendly)
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            // Permission revoked mid-session — silently ignore
        }
    }

    /** Call from onStop() to stop receiving updates and save battery. */
    fun stopUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
