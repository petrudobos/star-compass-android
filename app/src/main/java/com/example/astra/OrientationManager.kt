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

class OrientationManager(val context: Context, private val errorTracker: ErrorTracker? = null) : SensorEventListener {
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
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationVector = event.values
            val rawMatrix = FloatArray(16)
            SensorManager.getRotationMatrixFromVector(rawMatrix, rotationVector)

            val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try { context.display?.rotation ?: Surface.ROTATION_0 } catch (e: Exception) { Surface.ROTATION_0 }
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.rotation
            }

            val axisX: Int
            val axisY: Int
            when (rotation) {
                Surface.ROTATION_90 -> { // USB on right
                    axisX = SensorManager.AXIS_Y
                    axisY = SensorManager.AXIS_MINUS_X
                }
                Surface.ROTATION_180 -> {
                    axisX = SensorManager.AXIS_MINUS_X
                    axisY = SensorManager.AXIS_MINUS_Y
                }
                Surface.ROTATION_270 -> {
                    axisX = SensorManager.AXIS_MINUS_Y
                    axisY = SensorManager.AXIS_X
                }
                else -> { // Portrait
                    axisX = SensorManager.AXIS_X
                    axisY = SensorManager.AXIS_Y
                }
            }

            val remapped = FloatArray(16)
            SensorManager.remapCoordinateSystem(rawMatrix, axisX, axisY, remapped)
            
            // SkyCanvas world coords: X=East, Y=Up, Z=North
            // SensorManager world coords: X=East, Y=North, Z=Up
            
            // We need a matrix that transforms (East, Up, North) vectors to device space.
            // The 'remapped' matrix transforms (East, North, Up) to device space.
            // So we permute columns of 'remapped' to match (E, Up, N).
            // Col 0: East (remapped Col 0)
            // Col 1: Up   (remapped Col 2)
            // Col 2: North (remapped Col 1)
            
            val worldToDevice = FloatArray(16)
            // Column 0
            worldToDevice[0] = remapped[0]; worldToDevice[1] = remapped[1]; worldToDevice[2] = remapped[2]; worldToDevice[3] = 0f
            // Column 1 (was Up, now in Col 2 of remapped)
            worldToDevice[4] = remapped[8]; worldToDevice[5] = remapped[9]; worldToDevice[6] = remapped[10]; worldToDevice[7] = 0f
            // Column 2 (was North, now in Col 1 of remapped)
            worldToDevice[8] = remapped[4]; worldToDevice[9] = remapped[5]; worldToDevice[10] = remapped[6]; worldToDevice[11] = 0f
            worldToDevice[15] = 1f

            // Transpose to get World-to-Device rotation matrix
            val finalMatrix = FloatArray(16)
            android.opengl.Matrix.transposeM(finalMatrix, 0, worldToDevice, 0)
            
            _rotationMatrix.value = finalMatrix
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
