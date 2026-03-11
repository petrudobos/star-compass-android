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
 * 90-degree rotation compensation for landscape mode (USB on right).
 * 
 * Key insight: In landscape mode with USB on right, the device's physical X-axis
 * points UP on screen, and the physical Y-axis points LEFT on screen.
 * We need to rotate the geomagnetic coordinate system 90° clockwise to align
 * with the screen coordinate system for accurate compass-to-screen mapping.
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

        // Get base rotation matrix from sensor
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
                // Landscape: USB on right
                // This is our target orientation
                // Device X-axis (right when portrait) now points UP on screen
                // Device Y-axis (up when portrait) now points LEFT on screen
                // We need to remap so that:
                // - World North maps correctly to screen left/right movement
                // - World Up maps correctly to screen up/down movement
                SensorManager.remapCoordinateSystem(
                    sensorMatrix,
                    SensorManager.AXIS_Y,        // Old Y (North in portrait) becomes new X
                    SensorManager.AXIS_MINUS_X,  // Old -X (West in portrait) becomes new Y
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

        // The remappedMatrix now represents device-to-world transformation
        // For landscape (ROTATION_90), the matrix columns are:
        // Column 0: Direction that device's screen-right points to in world space
        // Column 1: Direction that device's screen-up points to in world space  
        // Column 2: Direction that device's screen-forward points to in world space
        
        // Our sky canvas coordinate system:
        // X-axis = East
        // Y-axis = Up (zenith)
        // Z-axis = North
        
        // We want a matrix that transforms world coordinates to screen coordinates
        // The columns should represent where world axes appear on screen
        
        val finalMatrix = FloatArray(16)
        
        // Extract the basis vectors from remapped matrix
        // These tell us where world directions point in device space
        
        // For our rendering:
        // Column 0: Where East points in screen coordinates
        finalMatrix[0] = remappedMatrix[0]   // East -> Screen X component
        finalMatrix[1] = remappedMatrix[4]   // East -> Screen Y component  
        finalMatrix[2] = remappedMatrix[8]   // East -> Screen Z component
        finalMatrix[3] = 0f
        
        // Column 1: Where Up points in screen coordinates
        finalMatrix[4] = remappedMatrix[2]   // Up -> Screen X component
        finalMatrix[5] = remappedMatrix[6]   // Up -> Screen Y component
        finalMatrix[6] = remappedMatrix[10]  // Up -> Screen Z component
        finalMatrix[7] = 0f
        
        // Column 2: Where North points in screen coordinates
        finalMatrix[8] = remappedMatrix[1]   // North -> Screen X component
        finalMatrix[9] = remappedMatrix[5]   // North -> Screen Y component
        finalMatrix[10] = remappedMatrix[9]  // North -> Screen Z component
        finalMatrix[11] = 0f
        
        // No translation
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
