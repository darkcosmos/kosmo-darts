package io.github.darkcosmos.kosmodarts.engine.player

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** A color in OKLab (lightness, green-red, blue-yellow). */
internal data class Lab(val l: Double, val a: Double, val b: Double)

/** Color conversions between sRGB and OKLab / OKLCH (Bjorn Ottosson's reference matrices). */
internal object Oklab {
    private const val BYTE_MAX = 255.0
    private const val GAMMA_THRESHOLD = 0.04045
    private const val GAMMA_LINEAR_SCALE = 12.92
    private const val GAMMA_OFFSET = 0.055
    private const val GAMMA_SCALE = 1.055
    private const val GAMMA_EXPONENT = 2.4
    private const val LINEAR_THRESHOLD = 0.0031308
    private const val GAMUT_EPSILON = 1e-6
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BYTE_MASK = 0xFF

    /** sRGB channel (0 to 1) to linear light. */
    fun toLinear(channel: Double): Double =
        if (channel <= GAMMA_THRESHOLD) {
            channel / GAMMA_LINEAR_SCALE
        } else {
            ((channel + GAMMA_OFFSET) / GAMMA_SCALE).pow(GAMMA_EXPONENT)
        }

    private fun fromLinear(linear: Double): Double =
        if (linear <= LINEAR_THRESHOLD) {
            linear * GAMMA_LINEAR_SCALE
        } else {
            GAMMA_SCALE * linear.pow(1 / GAMMA_EXPONENT) - GAMMA_OFFSET
        }

    fun linearRgb(color: PlayerColor): Triple<Double, Double, Double> = Triple(
        toLinear(((color.argb shr RED_SHIFT) and BYTE_MASK) / BYTE_MAX),
        toLinear(((color.argb shr GREEN_SHIFT) and BYTE_MASK) / BYTE_MAX),
        toLinear((color.argb and BYTE_MASK) / BYTE_MAX),
    )

    fun fromColor(color: PlayerColor): Lab {
        val (r, g, b) = linearRgb(color)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return Lab(
            l = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            b = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    /** The sRGB color of an OKLCH value (hue in radians), or `null` when it is outside the sRGB gamut. */
    fun toColorOrNull(lightness: Double, chroma: Double, hueRadians: Double): PlayerColor? {
        val a = chroma * cos(hueRadians)
        val b = chroma * sin(hueRadians)
        val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        val channels = listOf(
            4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        )
        if (channels.any { it < -GAMUT_EPSILON || it > 1 + GAMUT_EPSILON }) return null
        val bytes = channels.map { (fromLinear(it.coerceIn(0.0, 1.0)) * BYTE_MAX).roundToInt() }
        return PlayerColor.fromStored((bytes[0] shl RED_SHIFT) or (bytes[1] shl GREEN_SHIFT) or bytes[2])
    }

    fun distance(first: Lab, second: Lab): Double =
        kotlin.math.sqrt(
            abs(first.l - second.l).pow(2) + abs(first.a - second.a).pow(2) + abs(first.b - second.b).pow(2),
        )
}
