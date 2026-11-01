package io.github.darkcosmos.kosmodarts.capture.camera

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.github.darkcosmos.kosmodarts.capture.truth.Gravity

/** Keeps the latest `Sensor.TYPE_GRAVITY` vector (rule CP3); [latest] is null when the phone has no such sensor. */
class GravityReader(private val manager: SensorManager?) : SensorEventListener {
    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)

    @Volatile
    private var last: Gravity? = null

    /** The latest reading, or null when there is none yet or no sensor. */
    fun latest(): Gravity? = last

    /** Starts listening (screen visible). */
    fun start() {
        sensor?.let { manager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    /** Stops listening and forgets the reading. */
    fun stop() {
        manager?.unregisterListener(this)
        last = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        if (v.size >= 3 && v.take(3).all { it.isFinite() }) last = Gravity(v[0], v[1], v[2])
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
