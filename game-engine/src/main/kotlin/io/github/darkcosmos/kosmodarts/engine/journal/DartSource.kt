package io.github.darkcosmos.kosmodarts.engine.journal

/** How a dart entered the journal (DJ1). */
enum class DartSource {
    /** Typed on the keypad or tapped on the board. */
    MANUAL,

    /** Reserved for the later automatic detection with the camera. */
    CAMERA,
}
