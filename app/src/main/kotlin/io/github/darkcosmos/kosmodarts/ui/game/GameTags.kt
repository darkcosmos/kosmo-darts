package io.github.darkcosmos.kosmodarts.ui.game

/** Test tags of the game screen. */
object GameTags {
    const val BUST: String = "game_bust"
    const val CURRENT_PLAYER: String = "game_current_player"
    const val CURRENT_REMAINING: String = "game_current_remaining"
    const val LAST_TURN: String = "game_last_turn"

    fun dartSlot(index: Int): String = "game_dart_slot_$index"
}
