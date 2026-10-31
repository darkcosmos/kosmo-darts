package io.github.darkcosmos.kosmodarts.capture.ui.keypad

// Copied from :app (issue #41, decision Q11); not kept in sync.

/** Test tags of the keypad. */
object KeypadTags {
    const val DOUBLE: String = "key_double"
    const val TRIPLE: String = "key_triple"
    const val OUTER_BULL: String = "key_outer_bull"
    const val BULL: String = "key_bull"
    const val MISS: String = "key_miss"
    const val UNDO: String = "key_undo"
    const val NEXT_PLAYER: String = "key_next_player"

    fun number(number: Int): String = "key_$number"
}
