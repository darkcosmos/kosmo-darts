package io.github.darkcosmos.kosmodarts.ui.game

/** Test tags of the game screen. */
object GameTags {
    /** The whole screen content: it scrolls only below the score floor (layout policy, step 4). */
    const val SCREEN: String = "game_screen"

    /** The current player's zone. */
    const val CURRENT_PLAYER: String = "game_current_player"

    /** The score of the zone. */
    const val CURRENT_REMAINING: String = "game_current_remaining"

    /** The BUST banner of the zone (VT13). */
    const val CARD_BUST: String = "game_card_bust"

    /** The CHECKOUT banner and its line, one live region (design system 6.3, U5). */
    const val CHECKOUT_BANNER: String = "game_checkout_banner"

    /** "141 IN 3 DARTS" under the CHECKOUT banner (U2). */
    const val CHECKOUT_LINE: String = "game_checkout_line"

    /** The route area of the zone, always present (its reserved height, CS8). */
    const val CHECKOUT_SUGGESTION_LINE: String = "game_checkout_suggestion_line"

    /** The darts of the route inside [CHECKOUT_SUGGESTION_LINE]; absent when there is no route. */
    const val CHECKOUT_SUGGESTION: String = "game_checkout_suggestion"

    /** The `accent` underline of the next dart slot. */
    const val NEXT_DART_MARK: String = "game_next_dart_mark"

    /** The match position at the end of the header (M14, M19). */
    const val HEADER_POSITION: String = "game_header_position"

    const val NO_VOICE_DIALOG: String = "game_no_voice_dialog"

    /** The main button of the action bar while a checkout waits: "Confirm leg", "Confirm set" or "Confirm match". */
    const val CONFIRM: String = "game_confirm"

    fun dartSlot(index: Int): String = "game_dart_slot_$index"

    fun average(playerIndex: Int): String = "game_average_$playerIndex"

    fun visitTotal(playerIndex: Int): String = "game_visit_total_$playerIndex"

    fun playerName(playerIndex: Int): String = "game_player_name_$playerIndex"

    /** The chips of the zone or the match line of a tile (M14). */
    fun matchIndicators(playerIndex: Int): String = "game_match_indicators_$playerIndex"

    /** The tile of a player who is not throwing (M6). */
    fun tile(playerIndex: Int): String = "game_tile_$playerIndex"

    /** Remaining score of a tile; the zone uses [CURRENT_REMAINING]. */
    fun remaining(playerIndex: Int): String = "game_remaining_$playerIndex"
}
