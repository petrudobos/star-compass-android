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

class OrientationManager(
    val context: Context,
    private val errorTracker: ErrorTracker? = null
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val _rotationMatrix = MutableStateFlow(FloatArray(16).apply {
        this[0] = 1f; this[5] = 1f; this[10] = 1f; this[15] = 1f
    })
    val rotationMatrix = _rotationMatrix.asStateFlow()

    fun start() {
        if (rotationSensor == null) {
            errorTracker?.reportError(AppError.SensorError("Rotation Vector"))
            return
        }
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ROTATION_VECTOR) return

        val rawMatrix = FloatArray(16)
        SensorManager.getRotationMatrixFromVector(rawMatrix, event.values)

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

        val axisX: Int
        val axisY: Int
        when (rotation) {
            Surface.ROTATION_90 -> {
                axisX = SensorManager.AXIS_Y
                axisY = SensorManager.AXIS_MINUS_X
            }
            Surface.ROTATION_270 -> {
                axisX = SensorManager.AXIS_MINUS_Y
                axisY = SensorManager.AXIS_X
            }
            Surface.ROTATION_180 -> {
                axisX = SensorManager.AXIS_MINUS_X
                axisY = SensorManager.AXIS_MINUS_Y
            }
            else -> {
                axisX = SensorManager.AXIS_X
                axisY = SensorManager.AXIS_Y
            }
        }

        val remapped = FloatArray(16)
        SensorManager.remapCoordinateSystem(rawMatrix, axisX, axisY, remapped)

        // remapped is ScreenToWorld_Sensor
        // We want R such that Screen = R * World_Canvas
        // World_Canvas basis: X=East, Y=Up, Z=North
        // World_Sensor basis: X=East, Y=North, Z=Up
        
        // Transpose of remapped (World_SensorToScreen):
        // Col 0: East_in_Screen
        // Col 1: North_in_Screen
        // Col 2: Up_in_Screen
        
        // We want a matrix R where:
        // Col 0: East_in_Screen (remapped Row 0)
        // Col 1: Up_in_Screen   (remapped Row 2)
        // Col 2: North_in_Screen (remapped Row 1)
        
        val rotationMatrix = FloatArray(16)
        // Col 0 (East)
        rotationMatrix[0] = remapped[0]
        rotationMatrix[1] = remapped[4]
        rotationMatrix[2] = remapped[8]
        rotationMatrix[3] = 0f
        
        // Col 1 (Up)
        rotationMatrix[4] = remapped[2]
        rotationMatrix[5] = remapped[6]
        rotationMatrix[6] = remapped[10]
        rotationMatrix[7] = 0f
        
        // Col 2 (North)
        rotationMatrix[8] = remapped[1]
        rotationMatrix[9] = remapped[5]
        rotationMatrix[10] = remapped[9]
        rotationMatrix[11] = 0f
        
        rotationMatrix[15] = 1f

        _rotationMatrix.value = rotationMatrix
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
