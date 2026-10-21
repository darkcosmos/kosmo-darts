package io.github.darkcosmos.kosmodarts.ui.game

/** Test tags of the game screen. */
object GameTags {
    const val CURRENT_PLAYER: String = "game_current_player"
    const val CURRENT_REMAINING: String = "game_current_remaining"
    const val CARD_BUST: String = "game_card_bust"
    const val CHECKOUT_DIALOG: String = "game_checkout_dialog"

    /** The always-present suggestion line of the highlighted card (its reserved height). */
    const val CHECKOUT_SUGGESTION_LINE: String = "game_checkout_suggestion_line"

    /** The suggestion text inside [CHECKOUT_SUGGESTION_LINE]; absent from the merged tree when there is no route. */
    const val CHECKOUT_SUGGESTION: String = "game_checkout_suggestion"
    const val NO_VOICE_DIALOG: String = "game_no_voice_dialog"

    fun dartSlot(index: Int): String = "game_dart_slot_$index"

    fun average(playerIndex: Int): String = "game_average_$playerIndex"

    fun visitTotal(playerIndex: Int): String = "game_visit_total_$playerIndex"

    fun playerName(playerIndex: Int): String = "game_player_name_$playerIndex"

    /** Remaining score of a player row; the highlighted card uses [CURRENT_REMAINING]. */
    fun remaining(playerIndex: Int): String = "game_remaining_$playerIndex"
}
