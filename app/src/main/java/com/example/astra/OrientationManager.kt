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

/**
 * Orientation manager using GEOMAGNETIC_ROTATION_VECTOR sensor.
 * 
 * This sensor combines magnetometer (compass) + accelerometer WITHOUT gyroscope,
 * giving maximum compass influence and avoiding gyroscope drift.
 * 
 * Benefits:
 * - Direct magnetic north alignment (compass-first)
 * - No gyroscope drift accumulation
 * - Simpler calibration (just figure-8 pattern for magnetometer)
 * - More stable for AR star tracking
 */
class OrientationManager(
    val context: Context,
    private val errorTracker: ErrorTracker? = null
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    // Geomagnetic rotation vector: magnetometer + accelerometer (no gyro)
    private val geomagneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) // Fallback
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // Low-pass filter constant - REDUCED for smoother movement
    // Lower = smoother but slightly slower response
    // 0.10 = smooth tracking, good for star gazing
    private val ALPHA = 0.10f

    private var lastRotationVector = FloatArray(5)
    private var isInitialized = false

    private val _rotationMatrix = MutableStateFlow(FloatArray(16).apply {
        this[0] = 1f; this[5] = 1f; this[10] = 1f; this[15] = 1f
    })
    val rotationMatrix = _rotationMatrix.asStateFlow()

    // Track calibration time (first 10-15 seconds)
    private var startTime = 0L
    private var isCalibrating = true

    fun start() {
        if (geomagneticSensor == null) {
            errorTracker?.reportError(AppError.SensorError("Geomagnetic Rotation Vector"))
            return
        }
        
        startTime = System.currentTimeMillis()
        isCalibrating = true
        
        // Use SENSOR_DELAY_GAME for good balance between smoothness and battery
        sensorManager.registerListener(this, geomagneticSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        // Check if we're past calibration period (15 seconds)
        if (isCalibrating && System.currentTimeMillis() - startTime > 15000) {
            isCalibrating = false
        }

        // Accept both geomagnetic and regular rotation vector
        if (event.sensor.type != Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR &&
            event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) {
            return
        }

        // Apply low-pass filter for smooth movement
        if (!isInitialized) {
            // First reading: initialize without filtering
            System.arraycopy(event.values, 0, lastRotationVector, 0, 
                minOf(event.values.size, lastRotationVector.size))
            isInitialized = true
        } else {
            // Subsequent readings: apply low-pass filter
            for (i in 0 until minOf(event.values.size, lastRotationVector.size)) {
                lastRotationVector[i] = ALPHA * event.values[i] + (1 - ALPHA) * lastRotationVector[i]
            }
        }

        // Convert rotation vector to rotation matrix
        val rawMatrix = FloatArray(16)
        SensorManager.getRotationMatrixFromVector(rawMatrix, lastRotationVector)

        // Get device rotation to handle screen orientation
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

        // Remap coordinate system based on device rotation
        val remapped = FloatArray(16)
        when (rotation) {
            Surface.ROTATION_90 -> {
                // Landscape: USB on right (our target orientation)
                SensorManager.remapCoordinateSystem(
                    rawMatrix,
                    SensorManager.AXIS_Y,
                    SensorManager.AXIS_MINUS_X,
                    remapped
                )
            }
            Surface.ROTATION_270 -> {
                // Landscape: USB on left
                SensorManager.remapCoordinateSystem(
                    rawMatrix,
                    SensorManager.AXIS_MINUS_Y,
                    SensorManager.AXIS_X,
                    remapped
                )
            }
            Surface.ROTATION_180 -> {
                // Upside down portrait
                SensorManager.remapCoordinateSystem(
                    rawMatrix,
                    SensorManager.AXIS_MINUS_X,
                    SensorManager.AXIS_MINUS_Y,
                    remapped
                )
            }
            else -> {
                // Portrait (normal)
                SensorManager.remapCoordinateSystem(
                    rawMatrix,
                    SensorManager.AXIS_X,
                    SensorManager.AXIS_Y,
                    remapped
                )
            }
        }

        // Convert to our sky coordinate system
        // remapped gives us device-to-world transformation
        // Columns of remapped represent:
        // Col 0: East direction in device space
        // Col 1: North direction in device space
        // Col 2: Up direction in device space
        
        // Our sky canvas uses: X=East, Y=Up, Z=North
        val matrix = FloatArray(16)
        
        // Column 0: East (X-axis)
        matrix[0] = remapped[0]   // East X
        matrix[1] = remapped[4]   // East Y
        matrix[2] = remapped[8]   // East Z
        matrix[3] = 0f
        
        // Column 1: Up (Y-axis)
        matrix[4] = remapped[2]   // Up X
        matrix[5] = remapped[6]   // Up Y
        matrix[6] = remapped[10]  // Up Z
        matrix[7] = 0f
        
        // Column 2: North (Z-axis)
        matrix[8] = remapped[1]   // North X
        matrix[9] = remapped[5]   // North Y
        matrix[10] = remapped[9]  // North Z
        matrix[11] = 0f
        
        // Translation (none)
        matrix[12] = 0f
        matrix[13] = 0f
        matrix[14] = 0f
        matrix[15] = 1f

        _rotationMatrix.value = matrix
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Monitor compass accuracy during calibration period
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD || 
            sensor?.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
            
            when (accuracy) {
                SensorManager.SENSOR_STATUS_UNRELIABLE -> {
                    if (isCalibrating) {
                        // User should continue figure-8 calibration
                    }
                }
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> {
                    if (isCalibrating) {
                        // Keep calibrating
                    }
                }
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> {
                    // Good accuracy achieved
                    isCalibrating = false
                }
            }
        }
    }
}
