package com.example.astra

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import com.example.astra.ui.AppError
import com.example.astra.ui.ErrorTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.sin

/**
 * Orientation manager using GEOMAGNETIC_ROTATION_VECTOR sensor with proper
 * landscape mode (USB on right) coordinate system transformation.
 * 
 * Key insight for landscape (USB on right):
 * - Device's physical X-axis (right in portrait) points UP on screen
 * - Device's physical Y-axis (up in portrait) points LEFT on screen
 * - Device's physical Z-axis (out in portrait) points OUT of screen
 * 
 * Geomagnetic coordinate system:
 * - X points East
 * - Y points North  
 * - Z points Up (to sky)
 * 
 * For accurate compass tracking, we need to properly remap the geomagnetic
 * readings to match the device's physical orientation in landscape mode.
 */
class OrientationManager(
    val context: Context,
    private val errorTracker: ErrorTracker? = null
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    // Geomagnetic rotation vector: magnetometer + accelerometer (no gyro drift)
    private val geomagneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) // Fallback
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // Smoother filtering for stable star tracking
    private val ALPHA = 0.10f

    private var lastRotationVector = FloatArray(5)
    private var isInitialized = false

    private val _rotationMatrix = MutableStateFlow(FloatArray(16).apply {
        this[0] = 1f; this[5] = 1f; this[10] = 1f; this[15] = 1f
    })
    val rotationMatrix = _rotationMatrix.asStateFlow()

    private var startTime = 0L
    private var isCalibrating = true

    fun start() {
        if (geomagneticSensor == null) {
            errorTracker?.reportError(AppError.SensorError("Geomagnetic Rotation Vector"))
            return
        }
        
        startTime = System.currentTimeMillis()
        isCalibrating = true
        
        sensorManager.registerListener(this, geomagneticSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        if (isCalibrating && System.currentTimeMillis() - startTime > 15000) {
            isCalibrating = false
        }

        if (event.sensor.type != Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR &&
            event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) {
            return
        }

        // Low-pass filter for smooth movement
        if (!isInitialized) {
            System.arraycopy(event.values, 0, lastRotationVector, 0, 
                minOf(event.values.size, lastRotationVector.size))
            isInitialized = true
        } else {
            for (i in 0 until minOf(event.values.size, lastRotationVector.size)) {
                lastRotationVector[i] = ALPHA * event.values[i] + (1 - ALPHA) * lastRotationVector[i]
            }
        }

        // Get base rotation matrix from sensor (device-relative coordinates)
        val sensorMatrix = FloatArray(16)
        SensorManager.getRotationMatrixFromVector(sensorMatrix, lastRotationVector)

        // Get device screen rotation
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display?.rotation ?: Surface.ROTATION_0
            } catch (e: Exception) {
                Surface.ROTATION_0
            }
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

        // Apply coordinate remapping for screen orientation
        val remappedMatrix = FloatArray(16)
        when (rotation) {
            Surface.ROTATION_90 -> {
                // Landscape: USB on right (our primary mode)
                // In this orientation:
                // - Device X (right in portrait) -> screen UP
                // - Device Y (up in portrait) -> screen LEFT  
                // - Device Z (out in portrait) -> screen OUT
                //
                // We want:
                // - World X-axis (screen right) to align with East
                // - World Y-axis (screen up) to align with Zenith
                // - World Z-axis (screen forward) to align with North
                //
                // Correct remapping for landscape (USB right):
                SensorManager.remapCoordinateSystem(
                    sensorMatrix,
                    SensorManager.AXIS_Y,         // Device Y -> new X (screen right)
                    SensorManager.AXIS_MINUS_X,   // Device -X -> new Y (screen up)
                    remappedMatrix
                )
            }
            Surface.ROTATION_270 -> {
                // Landscape: USB on left
                SensorManager.remapCoordinateSystem(
                    sensorMatrix,
                    SensorManager.AXIS_MINUS_Y,
                    SensorManager.AXIS_X,
                    remappedMatrix
                )
            }
            Surface.ROTATION_180 -> {
                // Upside down portrait
                SensorManager.remapCoordinateSystem(
                    sensorMatrix,
                    SensorManager.AXIS_MINUS_X,
                    SensorManager.AXIS_MINUS_Y,
                    remappedMatrix
                )
            }
            else -> {
                // Portrait (normal)
                System.arraycopy(sensorMatrix, 0, remappedMatrix, 0, 16)
            }
        }

        // The remappedMatrix now represents device orientation relative to world
        // In the world coordinate system (geomagnetic):
        // - Column 0 (index 0,4,8): where device's +X axis points in world space
        // - Column 1 (index 1,5,9): where device's +Y axis points in world space  
        // - Column 2 (index 2,6,10): where device's +Z axis points in world space
        //
        // For rendering, we need the inverse transformation:
        // Our rendering coordinate system:
        // - X-axis = screen horizontal (East positive)
        // - Y-axis = screen vertical (Up/Zenith positive)
        // - Z-axis = screen depth (North positive, into screen)
        
        val finalMatrix = FloatArray(16)
        
        // Build camera/view matrix for star rendering
        // We need to transpose/invert the rotation part to get world-to-camera transform
        // Since rotation matrices are orthogonal, transpose = inverse for rotation part
        
        // Row 0: Camera right direction (where screen-right points in world)
        finalMatrix[0] = remappedMatrix[0]   // X component
        finalMatrix[1] = remappedMatrix[4]   // Y component
        finalMatrix[2] = remappedMatrix[8]   // Z component
        finalMatrix[3] = 0f
        
        // Row 1: Camera up direction (where screen-up points in world)
        finalMatrix[4] = remappedMatrix[1]   // X component
        finalMatrix[5] = remappedMatrix[5]   // Y component
        finalMatrix[6] = remappedMatrix[9]   // Z component
        finalMatrix[7] = 0f
        
        // Row 2: Camera forward direction (where screen-forward/into points in world)
        finalMatrix[8] = remappedMatrix[2]   // X component
        finalMatrix[9] = remappedMatrix[6]   // Y component
        finalMatrix[10] = remappedMatrix[10] // Z component
        finalMatrix[11] = 0f
        
        // Translation (no offset)
        finalMatrix[12] = 0f
        finalMatrix[13] = 0f
        finalMatrix[14] = 0f
        finalMatrix[15] = 1f

        _rotationMatrix.value = finalMatrix
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD || 
            sensor?.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
            
            when (accuracy) {
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> {
                    isCalibrating = false
                }
            }
        }
    }
}
