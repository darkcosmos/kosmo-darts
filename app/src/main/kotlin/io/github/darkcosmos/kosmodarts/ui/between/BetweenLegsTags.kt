package io.github.darkcosmos.kosmodarts.ui.between

/** Test tags of the between-legs screen. */
object BetweenLegsTags {
    const val WINNER: String = "between_winner"
    const val NEXT_STARTER: String = "between_next_starter"
    const val NEXT_LEG: String = "between_next_leg"
    const val SETS_HEADER: String = "between_sets_header"
    const val LEGS_HEADER: String = "between_legs_header"

    fun sets(playerIndex: Int): String = "between_sets_$playerIndex"

    fun legs(playerIndex: Int): String = "between_legs_$playerIndex"
}
