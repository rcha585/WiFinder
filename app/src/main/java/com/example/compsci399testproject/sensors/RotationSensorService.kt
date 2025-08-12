package com.example.compsci399testproject.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue


// TO ACCESS:
// We'll use the same RotationSensorService across both UI and particle filtering to preserve
// computing power, to use any of the 3 values, simply use RotationSensorService.azimuth (as an
// example, the others are .pitch, and .roll, depending on what you need).
class RotationSensorService(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    var azimuth by mutableFloatStateOf(0f) // Heading.
    var pitch by mutableFloatStateOf(0f) // Tilt forward/back.
    var roll by mutableFloatStateOf(0f) // Tilt left/right.
    var azimuthCompass by mutableFloatStateOf(0f) // Compass heading converted to degrees

    fun startListening() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            val orientations = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientations)

            azimuth = Math.toDegrees(orientations[0].toDouble()).toFloat()
            azimuthCompass = -((Math.toDegrees(orientations[0].toDouble()) + 360) % 360).toFloat()
            pitch = Math.toDegrees(orientations[1].toDouble()).toFloat()
            roll = Math.toDegrees(orientations[2].toDouble()).toFloat()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}