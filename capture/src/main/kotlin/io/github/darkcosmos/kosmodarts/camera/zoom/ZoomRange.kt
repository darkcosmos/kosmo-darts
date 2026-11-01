package io.github.darkcosmos.kosmodarts.camera.zoom

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.exp
import kotlin.math.ln

/** Smallest zoom change a person can pick: one tenth of a ratio. */
const val ZOOM_STEP = 0.1f

/** Zoom ratio of a camera that is not zoomed. */
const val DEFAULT_ZOOM_RATIO = 1f

private val STEP_DECIMAL = BigDecimal("0.1")

/**
 * Rounds a ratio to the nearest 0.1, half up, on its shortest decimal form (DZ10): 1.05f gives 1.1 and 2.35f gives
 * 2.4 although their binary values are slightly below. A non-finite ratio gives [DEFAULT_ZOOM_RATIO].
 *
 * This file must stay free of Android and capture types: only `kotlin.*` and `java.math.*` imports.
 */
fun quantizeZoom(ratio: Float): Float {
    if (!ratio.isFinite()) return DEFAULT_ZOOM_RATIO
    return BigDecimal(ratio.toString()).setScale(1, RoundingMode.HALF_UP).toFloat()
}

/**
 * The zoom ratios a camera accepts, with the logarithmic slider mapping of CP14 (DZ1).
 *
 * Build it with [of], which refuses invalid bounds. This type must stay free of Android and capture types.
 */
class ZoomRange private constructor(val min: Float, val max: Float) {
    /** True when the range is wide enough for at least one [ZOOM_STEP]. */
    val adjustable: Boolean =
        BigDecimal(max.toString()).subtract(BigDecimal(min.toString())) >= STEP_DECIMAL

    /** Rounds [ratio] to 0.1 then keeps it inside the range. */
    fun clamp(ratio: Float): Float = quantizeZoom(ratio).coerceIn(min, max)

    /** Ratio at slider [position] (0 to 1; NaN or out of range positions are clamped). */
    fun ratioAt(position: Float): Float {
        if (!adjustable) return clamp(min)
        val p = if (position.isNaN()) 0.0 else position.toDouble().coerceIn(0.0, 1.0)
        val ratio = min * exp(p * ln(max.toDouble() / min))
        return clamp(ratio.toFloat())
    }

    /** Slider position (0 to 1) of [ratio]; 0 when the range is not adjustable. */
    fun positionOf(ratio: Float): Float {
        if (!adjustable) return 0f
        val r = clamp(ratio).toDouble()
        return (ln(r / min) / ln(max.toDouble() / min)).coerceIn(0.0, 1.0).toFloat()
    }

    // Hand-written on purpose: a plain class (not a data class) keeps the private constructor and the checked
    // [of] factory as the only way in, which a generated copy() would bypass. Equality is by bounds only.
    override fun equals(other: Any?): Boolean = other is ZoomRange && other.min == min && other.max == max

    override fun hashCode(): Int = 31 * min.hashCode() + max.hashCode()

    override fun toString(): String = "ZoomRange($min..$max)"

    companion object {
        /** Null unless both bounds are finite and `0 < min <= max`. */
        fun of(min: Float, max: Float): ZoomRange? =
            if (min.isFinite() && max.isFinite() && min > 0f && max >= min) ZoomRange(min, max) else null
    }
}
