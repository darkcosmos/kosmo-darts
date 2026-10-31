package io.github.darkcosmos.kosmodarts.capture.truth

/**
 * Last reading of the phone's gravity sensor (`Sensor.TYPE_GRAVITY`) at a dart entry, in m/s² along the device axes.
 * Every component is a finite number.
 */
data class Gravity(val x: Float, val y: Float, val z: Float) {

    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "Gravity ($x, $y, $z) is not finite" }
    }
}
