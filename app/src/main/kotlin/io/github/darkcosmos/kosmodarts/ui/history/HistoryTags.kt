package io.github.darkcosmos.kosmodarts.ui.history

/** Test tags of the History screens. */
object HistoryTags {
    const val BACK = "history_back"
    const val LIST = "history_list"
    const val LOADING = "history_loading"
    const val EMPTY = "history_empty"
    const val NO_MATCH = "history_no_match"
    const val FAILED = "history_failed"
    const val RETRY = "history_retry"
    const val SHOW_ALL = "history_show_all"
    const val MODE_FILTER = "history_mode_filter"
    const val PLAYER_ALL = "history_player_all"

    const val DETAIL_BACK = "detail_back"
    const val DETAIL_SUMMARY = "detail_summary"
    const val DETAIL_LIST = "detail_list"
    const val DETAIL_FAILED = "detail_failed"
    const val DETAIL_RETRY = "detail_retry"

    /** The heading of the leg [ordinal] of the open match, from 0. */
    fun legHeading(ordinal: Int): String = "detail_leg_$ordinal"

    /** The visit number [index] (from 0) of the leg [ordinal]. */
    fun visit(ordinal: Int, index: Int): String = "detail_visit_${ordinal}_$index"

    /** The row of the match of session [sessionId]. */
    fun row(sessionId: Long): String = "history_row_$sessionId"

    /** The chip of the profile [profileId]. */
    fun playerChip(profileId: Long): String = "history_player_$profileId"
}
