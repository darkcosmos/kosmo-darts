package io.github.darkcosmos.kosmodarts.ui.home

/** Test tags of the home screen. */
object HomeTags {
    const val SUMMARY = "home_summary"

    fun player(index: Int): String = "home_summary_player_$index"

    fun score(index: Int): String = "home_summary_score_$index"
}
