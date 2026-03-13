package com.example.astra

import android.app.Application
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

class OrientationManager(
    application: Application,
    private val errorTracker: ErrorTracker? = null
) : SensorEventListener {

    private val context = application.applicationContext
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val geomagneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // Increased for faster, more responsive tracking without drift
    private val ALPHA = 0.20f

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
            event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        if (!isInitialized) {
            System.arraycopy(event.values, 0, lastRotationVector, 0,
                minOf(event.values.size, lastRotationVector.size))
            isInitialized = true
        } else {
            for (i in 0 until minOf(event.values.size, lastRotationVector.size)) {
                lastRotationVector[i] = ALPHA * event.values[i] + (1 - ALPHA) * lastRotationVector[i]
            }
        }

        val sensorMatrix = FloatArray(16)
        SensorManager.getRotationMatrixFromVector(sensorMatrix, lastRotationVector)

        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { context.display?.rotation ?: Surface.ROTATION_0 }
            catch (e: Exception) { Surface.ROTATION_0 }
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

        val remappedMatrix = FloatArray(16)
        when (rotation) {
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                sensorMatrix,
                SensorManager.AXIS_Y,
                SensorManager.AXIS_MINUS_X,
                remappedMatrix
            )
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                sensorMatrix,
                SensorManager.AXIS_MINUS_Y,
                SensorManager.AXIS_X,
                remappedMatrix
            )
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                sensorMatrix,
                SensorManager.AXIS_MINUS_X,
                SensorManager.AXIS_MINUS_Y,
                remappedMatrix
            )
            else -> System.arraycopy(sensorMatrix, 0, remappedMatrix, 0, 16)
        }

        // Transpose rotation part: world-to-device -> device-to-world (screen-to-world)
        val finalMatrix = FloatArray(16)
        finalMatrix[0]  = remappedMatrix[0];  finalMatrix[1]  = remappedMatrix[4];  finalMatrix[2]  = remappedMatrix[8];  finalMatrix[3]  = 0f
        finalMatrix[4]  = remappedMatrix[1];  finalMatrix[5]  = remappedMatrix[5];  finalMatrix[6]  = remappedMatrix[9];  finalMatrix[7]  = 0f
        finalMatrix[8]  = remappedMatrix[2];  finalMatrix[9]  = remappedMatrix[6];  finalMatrix[10] = remappedMatrix[10]; finalMatrix[11] = 0f
        finalMatrix[12] = 0f;                 finalMatrix[13] = 0f;                 finalMatrix[14] = 0f;                 finalMatrix[15] = 1f

        _rotationMatrix.value = finalMatrix
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD ||
            sensor?.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
            when (accuracy) {
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> isCalibrating = false
            }
        }
    }
}
