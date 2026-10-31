package io.github.darkcosmos.kosmodarts.capture.truth

import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/**
 * One dart entry of a session (rule CP3).
 *
 * [number] is the session-wide entry number, from 1, voided entries included; the photo is `dart-NNN.jpg` with this
 * number. [dart] is built by the engine, so a triple bull or segment 21 cannot exist (rule D2 of `docs/rules/x01.md`).
 * [voidedAt] is set when Undo voided the entry (rule CP4): the entry stays in the file. [photo] is null when the photo
 * failed (rule CP13); [gravity] is null when no sensor reading was available.
 */
data class TruthDart(
    val number: Int,
    val dart: Dart,
    val entry: Stamp,
    val voidedAt: Stamp?,
    val photo: TruthPhoto?,
    val gravity: Gravity?,
) {

    init {
        require(number >= 1) { "Dart number $number is not positive" }
    }

    /** Whether Undo voided this entry (rule CP4). */
    val voided: Boolean
        get() = voidedAt != null
}

/** The photo taken at a dart entry: its [file] name in the session folder and when it was [taken]. */
data class TruthPhoto(val file: String, val taken: Stamp) {

    init {
        require(file.isNotBlank()) { "Photo file name is blank" }
    }
}
