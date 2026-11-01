package io.github.darkcosmos.kosmodarts.camera.zoom

/**
 * Text form of a zoom ratio in a settings store (CP16), so any store reads and writes it the same way.
 * This object must stay free of Android and capture types.
 */
object ZoomSettingFormat {
    /** Ratio stored in [text]; [DEFAULT_ZOOM_RATIO] when missing, not a number, not finite or not positive. */
    fun parse(text: String?): Float {
        val value = text?.trim()?.toFloatOrNull()
        return if (value != null && value.isFinite() && value > 0f) quantizeZoom(value) else DEFAULT_ZOOM_RATIO
    }

    /** Text to store for [ratio]. */
    fun format(ratio: Float): String = quantizeZoom(ratio).toString()
}
