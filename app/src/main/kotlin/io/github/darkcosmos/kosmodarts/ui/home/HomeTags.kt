package io.github.darkcosmos.kosmodarts.ui.home

/** Test tags of the home screen. */
object HomeTags {
    const val SUMMARY = "home_summary"
    const val SETTINGS = "home_settings"

    fun player(index: Int): String = "home_summary_player_$index"

    fun score(index: Int): String = "home_summary_score_$index"

    /** The match score of a player (M18). */
    fun matchScore(index: Int): String = "home_summary_match_score_$index"
}
