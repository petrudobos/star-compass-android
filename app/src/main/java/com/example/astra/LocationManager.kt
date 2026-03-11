package com.example.astra

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.example.astra.ui.AppError
import com.example.astra.ui.ErrorTracker
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationManager(context: Context, private val errorTracker: ErrorTracker? = null) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    
    private val _location = MutableStateFlow<Location?>(null)
    val location = _location.asStateFlow()

    @SuppressLint("MissingPermission")
    fun requestSingleUpdate() {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        _location.value = loc
                    }
                    // Removed "Location is null" error to avoid startup annoyance
                }
                .addOnFailureListener { e ->
                    // Only report if it's not a simple lack of fix
                    errorTracker?.reportError(AppError.LocationError(e.message ?: "Unknown location error"))
                }
        } catch (e: Exception) {
            errorTracker?.reportError(AppError.LocationError(e.message ?: "Security exception or other error"))
        }
    }
}
