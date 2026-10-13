package io.github.darkcosmos.kosmodarts.ui.game

/** Test tags of the game screen. */
object GameTags {
    const val CURRENT_PLAYER: String = "game_current_player"
    const val CURRENT_REMAINING: String = "game_current_remaining"
    const val CARD_BUST: String = "game_card_bust"
    const val CHECKOUT_DIALOG: String = "game_checkout_dialog"

    fun dartSlot(index: Int): String = "game_dart_slot_$index"

    fun lastTurnScore(playerIndex: Int): String = "game_last_turn_score_$playerIndex"
}
