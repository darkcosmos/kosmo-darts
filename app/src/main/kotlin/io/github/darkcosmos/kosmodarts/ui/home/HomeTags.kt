package io.github.darkcosmos.kosmodarts.ui.home

/** Test tags of the home screen. */
object HomeTags {
    /** The "GAME IN PROGRESS" card (R2, M18). */
    const val SUMMARY = "home_summary"

    /** The title "GAME IN PROGRESS" and the format line of the card (U6). */
    const val SUMMARY_TITLE = "home_summary_title"
    const val SUMMARY_FORMAT = "home_summary_format"

    /** "RESUME · <player> TO THROW" (R2, owner Q11 of issue #49). */
    const val RESUME = "home_resume"

    /** The X01 mode card: it starts a new game (R3). */
    const val MODE_X01 = "home_mode_x01"

    /** The Cricket mode card (CR20). */
    const val MODE_CRICKET = "home_mode_cricket"
    const val SETTINGS = "home_settings"

    /** The "History" button (HS1). */
    const val HISTORY = "home_history"

    fun player(index: Int): String = "home_summary_player_$index"

    fun score(index: Int): String = "home_summary_score_$index"

    /** The match score of a player (M18). */
    fun matchScore(index: Int): String = "home_summary_match_score_$index"
}
