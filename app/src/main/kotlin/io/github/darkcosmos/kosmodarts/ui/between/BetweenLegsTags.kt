package io.github.darkcosmos.kosmodarts.ui.between

/** Test tags of the between-legs screen. */
object BetweenLegsTags {
    /** The chip "SET 2 · LEG 3 WON" (M19). */
    const val TITLE: String = "between_title"

    /** The winner's name. */
    const val WINNER: String = "between_winner"

    /** "CHECKOUT 141 · T20 T19 D12". */
    const val CHECKOUT: String = "between_checkout"

    /** The info bar "SET 2 · LEG 4 ▌ CID STARTS". */
    const val NEXT_STARTER: String = "between_next_starter"
    const val NEXT_LEG: String = "between_next_leg"
    const val SETS_HEADER: String = "between_sets_header"
    const val LEGS_HEADER: String = "between_legs_header"

    fun sets(playerIndex: Int): String = "between_sets_$playerIndex"

    fun legs(playerIndex: Int): String = "between_legs_$playerIndex"

    fun average(playerIndex: Int): String = "between_average_$playerIndex"
}
