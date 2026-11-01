package io.github.darkcosmos.kosmodarts.capture.truth

/** Size in pixels of the recorded videos or of the photos (rule CP11), both strictly positive. */
data class Resolution(val width: Int, val height: Int) {

    init {
        require(width > 0 && height > 0) { "Resolution ${width}x$height is not positive" }
    }
}
