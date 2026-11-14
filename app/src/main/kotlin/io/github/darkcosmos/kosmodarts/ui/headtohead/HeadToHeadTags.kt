package io.github.darkcosmos.kosmodarts.ui.headtohead

/** Test tags of the Head-to-head screen (the filter groups reuse the tags of `ProfileTags`). */
object HeadToHeadTags {
    const val BACK = "h2h_back"
    const val FAILED = "h2h_failed"
    const val RETRY = "h2h_retry"
    const val PICKER_TITLE = "h2h_picker_title"
    const val PICKER_LIST = "h2h_picker_list"
    const val PICKER_EMPTY = "h2h_picker_empty"
    const val TITLE = "h2h_title"
    const val SINCE = "h2h_since"
    const val IGNORED = "h2h_ignored"
    const val CHANGE = "h2h_change"
    const val NO_COMMON_MATCH = "h2h_no_common_match"
    const val CONTENT = "h2h_content"
    const val MODE_FILTER = "h2h_mode_filter"
    const val MATCH_LIST = "h2h_match_list"

    /** The picker row of the profile [id]. */
    fun pickerRow(id: Long): String = "h2h_picker_row_$id"

    /** The row of the common match [sessionId]. */
    fun matchRow(sessionId: Long): String = "h2h_match_$sessionId"

    /** The figure row [key] (for example `average`). */
    fun figure(key: String): String = "h2h_figure_$key"

    /** The block [key] (for example `results`, `x01`, `cricket`). */
    fun block(key: String): String = "h2h_block_$key"

    /** The name of the player at the left (`a`) or right (`b`) of the block [key]. */
    fun name(key: String, side: String): String = "h2h_name_${key}_$side"
}
