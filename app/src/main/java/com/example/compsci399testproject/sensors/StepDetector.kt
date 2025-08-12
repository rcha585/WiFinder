package com.example.compsci399testproject.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class StepDetectionService(
    context: Context,
    private val onStepDetected: () -> Unit,
    var useRhythm : Boolean = true

) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private val magnitudeHistory = mutableListOf<Float>()
    private val maxBufferSize = 20

    private var lastStepTime = 0L
    private val stepCooldown = 300

    fun startListening() {
        sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]
            val magnitude = sqrt(ax * ax + ay * ay + az * az)

            // Store recent magnitudes
            magnitudeHistory.add(magnitude)
            if (magnitudeHistory.size > maxBufferSize) {
                magnitudeHistory.removeAt(0)
            }

            if (useRhythm){
                detectStepFromRhythm()
            } else {
                detectStepFromStagger()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // STRATEGY 1: Detect rhythmic stepping via peak detection
    private fun detectStepFromRhythm() {
        if (magnitudeHistory.size < 3) return

        val prev = magnitudeHistory[magnitudeHistory.size - 3]
        val curr = magnitudeHistory[magnitudeHistory.size - 2]
        val next = magnitudeHistory[magnitudeHistory.size - 1]

        val isPeak = curr > prev && curr > next && curr > 1.2f // threshold may need to change.
        val now = System.currentTimeMillis()

        if (isPeak && now - lastStepTime > stepCooldown) {
            lastStepTime = now
            onStepDetected()
        }
    }

    // STRATEGY 2: Detect sudden fall + rebound (stagger)
    private fun detectStepFromStagger() {
        if (magnitudeHistory.size < 4) return
        val window = magnitudeHistory.takeLast(4)
        val change = window.last() - window.first()

        val isStagger = change > 1.5f || change < -1.5f
        val now = System.currentTimeMillis()

        if (isStagger && now - lastStepTime > stepCooldown) {
            lastStepTime = now
            onStepDetected()
        }
    }
}
