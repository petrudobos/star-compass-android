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
 * Simplified orientation manager that prioritizes compass (magnetometer) readings
 * for azimuth and uses accelerometer for device tilt.
 * 
 * This approach gives the phone's compass maximum influence over the AR rotation.
 */
class OrientationManager(
    val context: Context,
    private val errorTracker: ErrorTracker? = null
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // Raw sensor values with low-pass filtering
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var gravitySet = false
    private var geomagneticSet = false

    // Low-pass filter constant (0-1, lower = smoother but slower)
    private val ALPHA = 0.15f

    private val _rotationMatrix = MutableStateFlow(FloatArray(16).apply {
        this[0] = 1f; this[5] = 1f; this[10] = 1f; this[15] = 1f
    })
    val rotationMatrix = _rotationMatrix.asStateFlow()

    fun start() {
        if (accelerometer == null) {
            errorTracker?.reportError(AppError.SensorError("Accelerometer"))
            return
        }
        if (magnetometer == null) {
            errorTracker?.reportError(AppError.SensorError("Magnetometer"))
            return
        }
        
        // Register both sensors with GAME delay for good responsiveness
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // Low-pass filter for gravity
                gravity[0] = ALPHA * event.values[0] + (1 - ALPHA) * gravity[0]
                gravity[1] = ALPHA * event.values[1] + (1 - ALPHA) * gravity[1]
                gravity[2] = ALPHA * event.values[2] + (1 - ALPHA) * gravity[2]
                gravitySet = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                // Low-pass filter for magnetic field
                geomagnetic[0] = ALPHA * event.values[0] + (1 - ALPHA) * geomagnetic[0]
                geomagnetic[1] = ALPHA * event.values[1] + (1 - ALPHA) * geomagnetic[1]
                geomagnetic[2] = ALPHA * event.values[2] + (1 - ALPHA) * geomagnetic[2]
                geomagneticSet = true
            }
        }

        // Only calculate rotation matrix when both sensors have provided data
        if (gravitySet && geomagneticSet) {
            calculateRotationMatrix()
        }
    }

    private fun calculateRotationMatrix() {
        // Get rotation matrix from accelerometer and magnetometer
        // This gives us device orientation relative to Earth's magnetic north
        val R = FloatArray(9)
        val I = FloatArray(9)
        
        val success = SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)
        if (!success) return

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
        val remappedR = FloatArray(9)
        when (rotation) {
            Surface.ROTATION_90 -> {
                // Phone rotated 90° left (landscape, USB on right - our target orientation)
                SensorManager.remapCoordinateSystem(
                    R,
                    SensorManager.AXIS_Y,
                    SensorManager.AXIS_MINUS_X,
                    remappedR
                )
            }
            Surface.ROTATION_270 -> {
                // Phone rotated 90° right (landscape, USB on left)
                SensorManager.remapCoordinateSystem(
                    R,
                    SensorManager.AXIS_MINUS_Y,
                    SensorManager.AXIS_X,
                    remappedR
                )
            }
            Surface.ROTATION_180 -> {
                // Upside down portrait
                SensorManager.remapCoordinateSystem(
                    R,
                    SensorManager.AXIS_MINUS_X,
                    SensorManager.AXIS_MINUS_Y,
                    remappedR
                )
            }
            else -> {
                // Portrait (normal)
                SensorManager.remapCoordinateSystem(
                    R,
                    SensorManager.AXIS_X,
                    SensorManager.AXIS_Y,
                    remappedR
                )
            }
        }

        // Convert 3x3 rotation matrix to 4x4 for OpenGL-style usage
        // remappedR gives us device-to-world transformation
        // We want world-to-screen transformation for rendering
        
        // The remappedR matrix columns represent:
        // Column 0: East direction in device space
        // Column 1: North direction in device space  
        // Column 2: Up direction in device space
        
        // For our sky canvas coordinate system:
        // X = East, Y = Up, Z = North
        // We need to build a matrix that transforms from this to screen coordinates
        
        val matrix = FloatArray(16)
        
        // Column 0: East (X-axis) - directly from remappedR column 0
        matrix[0] = remappedR[0]  // East X component
        matrix[1] = remappedR[3]  // East Y component
        matrix[2] = remappedR[6]  // East Z component
        matrix[3] = 0f
        
        // Column 1: Up (Y-axis) - directly from remappedR column 2
        matrix[4] = remappedR[2]  // Up X component
        matrix[5] = remappedR[5]  // Up Y component
        matrix[6] = remappedR[8]  // Up Z component
        matrix[7] = 0f
        
        // Column 2: North (Z-axis) - directly from remappedR column 1
        matrix[8] = remappedR[1]   // North X component
        matrix[9] = remappedR[4]   // North Y component
        matrix[10] = remappedR[7]  // North Z component
        matrix[11] = 0f
        
        // Translation (none needed)
        matrix[12] = 0f
        matrix[13] = 0f
        matrix[14] = 0f
        matrix[15] = 1f

        _rotationMatrix.value = matrix
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Warn user if compass accuracy is poor
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            when (accuracy) {
                SensorManager.SENSOR_STATUS_UNRELIABLE -> {
                    // User should calibrate compass by moving phone in figure-8 pattern
                }
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> {
                    // Compass needs calibration
                }
                // ACCURACY_MEDIUM and ACCURACY_HIGH are acceptable
            }
        }
    }
}
