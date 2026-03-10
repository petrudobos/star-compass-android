package com.example.astra

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import com.example.astra.ui.AppError
import com.example.astra.ui.ErrorTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class OrientationManager(val context: Context, private val errorTracker: ErrorTracker? = null) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val _rotationMatrix = MutableStateFlow(FloatArray(16))
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
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationVector = event.values
            val rawMatrix = FloatArray(16)
            SensorManager.getRotationMatrixFromVector(rawMatrix, rotationVector)

            val adjustedMatrix = FloatArray(16)
            val worldAxisX: Int
            val worldAxisY: Int

            when (windowManager.defaultDisplay.rotation) {
                Surface.ROTATION_90 -> {
                    worldAxisX = SensorManager.AXIS_Y
                    worldAxisY = SensorManager.AXIS_MINUS_X
                }
                Surface.ROTATION_180 -> {
                    worldAxisX = SensorManager.AXIS_MINUS_X
                    worldAxisY = SensorManager.AXIS_MINUS_Y
                }
                Surface.ROTATION_270 -> {
                    worldAxisX = SensorManager.AXIS_MINUS_Y
                    worldAxisY = SensorManager.AXIS_X
                }
                else -> {
                    worldAxisX = SensorManager.AXIS_X
                    worldAxisY = SensorManager.AXIS_Y
                }
            }

            SensorManager.remapCoordinateSystem(rawMatrix, worldAxisX, worldAxisY, adjustedMatrix)
            _rotationMatrix.value = adjustedMatrix
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
