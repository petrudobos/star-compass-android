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
 * Key coordinate systems:
 * 
 * 1. Android sensor coordinates (world space):
 *    X: East
 *    Y: North
 *    Z: Up (zenith)
 * 
 * 2. Device physical axes (when in landscape, USB right):
 *    Device X: Points up on screen (was right in portrait)
 *    Device Y: Points left on screen (was up in portrait)
 *    Device Z: Points out of screen (unchanged)
 * 
 * 3. Our rendering coordinates (what SkyCanvas expects):
 *    Render X: Screen horizontal right = East
 *    Render Y: Screen vertical up = Zenith
 *    Render Z: Into screen (viewing direction) = North
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
    private val ALPHA = 0.15f // Slightly more responsive

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

        // Get base rotation matrix from sensor (world-to-device)
        // This gives us: device axes expressed in world coordinates
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

        // Remap for screen orientation
        val remappedMatrix = FloatArray(16)
        when (rotation) {
            Surface.ROTATION_90 -> {
                // Landscape: USB on right (primary mode)
                // The sensor matrix columns represent where device axes point in world space:
                // - Column 0 (indices 0,1,2): Device +X in world = points UP on screen in landscape
                // - Column 1 (indices 4,5,6): Device +Y in world = points LEFT on screen in landscape  
                // - Column 2 (indices 8,9,10): Device +Z in world = points OUT of screen
                //
                // We want screen axes to map to world:
                // - Screen right (+X) -> East (world +X)
                // - Screen up (+Y) -> Zenith (world +Z)
                // - Screen forward (+Z) -> North (world +Y)
                //
                // Remapping: specify which device axis becomes new X and Y
                // New X (screen right = East): use Device Y
                // New Y (screen up = Zenith): use Device -X (negative because device X points up, we want down to be -Y)
                SensorManager.remapCoordinateSystem(
                    sensorMatrix,
                    SensorManager.AXIS_Y,         // Device Y -> new X axis
                    SensorManager.AXIS_MINUS_X,   // Device -X -> new Y axis
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
                // Portrait (normal) - no remapping needed
                System.arraycopy(sensorMatrix, 0, remappedMatrix, 0, 16)
            }
        }

        // Now remappedMatrix is world-to-screen orientation matrix
        // Its columns tell us where world axes (East, North, Up) point on the screen
        //
        // For rendering, we need the TRANSPOSE (inverse for rotation)
        // This gives us screen-to-world, which tells us:
        // "When I point screen-right, where am I pointing in world space?"
        //
        // The transpose makes rows become columns:
        
        val finalMatrix = FloatArray(16)
        
        // Row 0: Where does screen +X (right) point in world space?
        // This should point East when phone faces North
        finalMatrix[0] = remappedMatrix[0]   // World X (East) component
        finalMatrix[1] = remappedMatrix[4]   // World Y (North) component  
        finalMatrix[2] = remappedMatrix[8]   // World Z (Up) component
        finalMatrix[3] = 0f
        
        // Row 1: Where does screen +Y (up) point in world space?
        // This should point Up (zenith) when phone is level
        finalMatrix[4] = remappedMatrix[1]   // World X component
        finalMatrix[5] = remappedMatrix[5]   // World Y component
        finalMatrix[6] = remappedMatrix[9]   // World Z component  
        finalMatrix[7] = 0f
        
        // Row 2: Where does screen +Z (into screen/viewing direction) point in world?
        // This should point North when phone faces North
        finalMatrix[8] = remappedMatrix[2]   // World X component
        finalMatrix[9] = remappedMatrix[6]   // World Y component
        finalMatrix[10] = remappedMatrix[10] // World Z component
        finalMatrix[11] = 0f
        
        // Row 3: Translation (origin, no offset)
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
