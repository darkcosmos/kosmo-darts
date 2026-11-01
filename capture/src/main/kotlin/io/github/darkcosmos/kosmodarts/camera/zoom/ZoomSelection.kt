package io.github.darkcosmos.kosmodarts.camera.zoom

import java.math.BigDecimal

/**
 * Immutable state of a zoom control: the ratio the person last chose ([remembered]) and the [range] the camera
 * offers, when known. This type must stay free of Android and capture types.
 */
class ZoomSelection(remembered: Float = DEFAULT_ZOOM_RATIO, val range: ZoomRange? = null) {
    /** The chosen ratio, rounded to 0.1; kept when the range changes so it is not lost. */
    val remembered: Float = quantizeZoom(remembered)

    /** Ratio to apply and show: [remembered], kept inside [range] when it is known. */
    val ratio: Float = range?.clamp(this.remembered) ?: this.remembered

    val adjustable: Boolean get() = range?.adjustable == true

    /** Slider position of [ratio]. */
    val position: Float get() = range?.positionOf(ratio) ?: 0f

    /** True when the camera range is known and offers no zoom. */
    val unavailable: Boolean get() = range != null && !adjustable

    val canStepDown: Boolean get() = range != null && adjustable && ratio > range.min

    val canStepUp: Boolean get() = range != null && adjustable && ratio < range.max

    /** Same remembered ratio with another [range]. */
    fun withRange(range: ZoomRange?): ZoomSelection = ZoomSelection(remembered, range)

    /** Selection moved to slider [position]; unchanged when not adjustable. */
    fun movedTo(position: Float): ZoomSelection {
        val current = range
        if (current == null || !current.adjustable) return this
        return ZoomSelection(current.ratioAt(position), current)
    }

    /** Selection moved by [steps] times 0.1 from the rounded current ratio; unchanged when not adjustable. */
    fun steppedBy(steps: Int): ZoomSelection {
        val current = range
        if (current == null || !current.adjustable) return this
        val target = BigDecimal(quantizeZoom(ratio).toString()) + BigDecimal("0.1") * BigDecimal(steps)
        return ZoomSelection(current.clamp(target.toFloat()), current)
    }

    override fun equals(other: Any?): Boolean =
        other is ZoomSelection && other.remembered == remembered && other.range == range

    override fun hashCode(): Int = 31 * remembered.hashCode() + (range?.hashCode() ?: 0)

    override fun toString(): String = "ZoomSelection($remembered, $range)"
}
