package io.github.darkcosmos.kosmodarts.ui.setup

/** Test tags of the setup screen. */
object SetupTags {
    /** Prefix of the "Legs per set" (or "Legs") stepper (M13). */
    const val LEGS: String = "setup_legs"

    /** Prefix of the "Sets" stepper (M13). */
    const val SETS: String = "setup_sets"

    fun nameField(index: Int): String = "setup_name_$index"

    /** Visible label of the stepper [prefix]. */
    fun label(prefix: String): String = "${prefix}_label"

    /** "First to N" of the stepper [prefix]. */
    fun value(prefix: String): String = "${prefix}_value"

    /** − of the stepper [prefix]. */
    fun decrease(prefix: String): String = "${prefix}_decrease"

    /** + of the stepper [prefix]. */
    fun increase(prefix: String): String = "${prefix}_increase"
}
