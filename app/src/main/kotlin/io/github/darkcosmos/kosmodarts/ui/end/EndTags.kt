package io.github.darkcosmos.kosmodarts.ui.end

/** Test tags of the end screen. Positions count ranking rows from 0. */
object EndTags {
    const val TITLE: String = "end_title"
    const val WINNER: String = "end_winner"
    const val DETAIL: String = "end_detail"
    const val REMATCH: String = "end_rematch"
    const val NEW_GAME: String = "end_new_game"

    /** The header of the score column: "SETS", or "LEGS" with one set (M17, owner Q8). */
    const val SCORE_HEADER: String = "end_score_header"
    const val DARTS_HEADER: String = "end_darts_header"

    /** The header of the Cricket PTS column (CR27); absent in a solo game. */
    const val POINTS_HEADER: String = "end_points_header"

    fun row(position: Int): String = "end_row_$position"

    /** Sets won, or legs won with one set; absent in a single game (M17). */
    fun score(position: Int): String = "end_score_$position"

    fun average(position: Int): String = "end_average_$position"

    /** Darts thrown over the match (M17, owner O2). */
    fun darts(position: Int): String = "end_darts_$position"

    /** Points of the last leg, Cricket only (CR27). */
    fun points(position: Int): String = "end_points_$position"
}
