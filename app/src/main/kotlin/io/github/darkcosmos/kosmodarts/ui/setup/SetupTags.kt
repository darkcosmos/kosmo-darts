package io.github.darkcosmos.kosmodarts.ui.setup

/** Test tags of the setup screen. */
object SetupTags {
    /** Prefix of the "Legs · first to" stepper (M13). */
    const val LEGS: String = "setup_legs"

    /** Prefix of the "Sets · first to" stepper (M13). */
    const val SETS: String = "setup_sets"

    const val BACK: String = "setup_back"
    const val SHUFFLE: String = "setup_shuffle"
    const val ADD_PLAYER: String = "setup_add_player"
    const val START: String = "setup_start"

    /** The scrolling content above the pinned "Start" (U7). */
    const val CONTENT: String = "setup_content"

    /** The hint line under the Cricket variant choice (CR21). */
    const val CRICKET_HINT: String = "setup_cricket_hint"

    /** The "25-Bull" switch of the Cricket setup (CR21). */
    const val CRICKET_BULL: String = "setup_cricket_bull"

    fun nameField(index: Int): String = "setup_name_$index"

    /** The remove cross of the row at [index] (M13, PC3). */
    fun remove(index: Int): String = "setup_remove_$index"

    /** Visible label of the stepper [prefix]. */
    fun label(prefix: String): String = "${prefix}_label"

    /** The value of the stepper [prefix]. */
    fun value(prefix: String): String = "${prefix}_value"

    /** − of the stepper [prefix]. */
    fun decrease(prefix: String): String = "${prefix}_decrease"

    /** + of the stepper [prefix]. */
    fun increase(prefix: String): String = "${prefix}_increase"
}
