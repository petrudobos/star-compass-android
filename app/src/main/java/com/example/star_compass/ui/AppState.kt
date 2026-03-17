package com.example.star_compass.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

sealed class AppError(val message: String, val recoverable: Boolean = true) {
    data class PermissionDenied(val permission: String) : AppError("Permission for $permission was denied", false)
    data class SensorError(val sensorName: String) : AppError("Sensor $sensorName not available")
    data class LocationError(val details: String) : AppError("Failed to get location: $details")
    data class CameraError(val details: String) : AppError("Camera error: $details")
}

class ErrorTracker {
    val errors = mutableStateListOf<AppError>()

    fun reportError(error: AppError) {
        if (!errors.contains(error)) {
            errors.add(error)
        }
    }

    fun dismissError(error: AppError) {
        errors.remove(error)
    }
}

@Composable
fun rememberErrorTracker() = remember { ErrorTracker() }
