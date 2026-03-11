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

            // Remap sensor axes to match the device's physical orientation.
            // Goal: after remapping, matrix[0..2] = device-X, matrix[4..6] = device-Y, matrix[8..10] = device-Z
            // For a landscape app (fixed ROTATION_90 = USB on right side):
            //   Device screen-right (X) corresponds to sensor North (+Y)
            //   Device screen-up    (Y) corresponds to sensor East  (+X)   <- this is the key fix for diagonal issue
            // For ROTATION_270 (USB on left):
            //   Device screen-right (X) = sensor -North (-Y)
            //   Device screen-up    (Y) = sensor -East  (-X)
            val axisX: Int
            val axisY: Int
            when (rotation) {
                Surface.ROTATION_90 -> {  // Landscape, USB/volume on right (natural landscape for Poco F7)
                    axisX = SensorManager.AXIS_Y
                    axisY = SensorManager.AXIS_X
                }
                Surface.ROTATION_270 -> { // Landscape, USB/volume on left
                    axisX = SensorManager.AXIS_MINUS_Y
                    axisY = SensorManager.AXIS_MINUS_X
                }
                Surface.ROTATION_180 -> {
                    axisX = SensorManager.AXIS_MINUS_X
                    axisY = SensorManager.AXIS_MINUS_Y
                }
                else -> { // Portrait (ROTATION_0)
                    axisX = SensorManager.AXIS_X
                    axisY = SensorManager.AXIS_Y
                }
            }

            val remapped = FloatArray(16)
            SensorManager.remapCoordinateSystem(rawMatrix, axisX, axisY, remapped)

            // SkyCanvas world coords: X=East, Y=Up, Z=North  (E, Up, N)
            // SensorManager world coords after remap: X=East, Y=North, Z=Up  (E, N, Up)
            //
            // Permute columns of 'remapped' so that:
            //   New Col 0 = East  = old Col 0  (no change)
            //   New Col 1 = Up    = old Col 2  (was Z/Up)
            //   New Col 2 = North = old Col 1  (was Y/North)
            //
            // Memory layout (column-major 4x4):
            //   Col 0: indices 0,1,2,3
            //   Col 1: indices 4,5,6,7
            //   Col 2: indices 8,9,10,11
            //   Col 3: indices 12,13,14,15

            val worldToDevice = FloatArray(16)
            // Col 0 = East (unchanged from remapped Col 0)
            worldToDevice[0] = remapped[0];  worldToDevice[1] = remapped[1];  worldToDevice[2] = remapped[2];  worldToDevice[3] = 0f
            // Col 1 = Up (from remapped Col 2 = indices 8,9,10)
            worldToDevice[4] = remapped[8];  worldToDevice[5] = remapped[9];  worldToDevice[6] = remapped[10]; worldToDevice[7] = 0f
            // Col 2 = North (from remapped Col 1 = indices 4,5,6)
            worldToDevice[8] = remapped[4];  worldToDevice[9] = remapped[5];  worldToDevice[10] = remapped[6]; worldToDevice[11] = 0f
            worldToDevice[15] = 1f

            // Transpose to get Device-to-World -> used in SkyCanvas as projection matrix
            val finalMatrix = FloatArray(16)
            android.opengl.Matrix.transposeM(finalMatrix, 0, worldToDevice, 0)

            _rotationMatrix.value = finalMatrix
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
