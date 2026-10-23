package io.github.darkcosmos.kosmodarts.ui.home

/** Test tags of the home screen. */
object HomeTags {
    /** The "GAME IN PROGRESS" card (R2, M18). */
    const val SUMMARY = "home_summary"

    /** "RESUME · <player> TO THROW" (R2, owner Q11 of issue #49). */
    const val RESUME = "home_resume"

    /** The X01 mode card: it starts a new game (R3). */
    const val MODE_X01 = "home_mode_x01"
    const val SETTINGS = "home_settings"

    fun player(index: Int): String = "home_summary_player_$index"

    fun score(index: Int): String = "home_summary_score_$index"

    /** The match score of a player (M18). */
    fun matchScore(index: Int): String = "home_summary_match_score_$index"
}
