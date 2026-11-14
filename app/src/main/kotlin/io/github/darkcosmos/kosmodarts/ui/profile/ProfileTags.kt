package io.github.darkcosmos.kosmodarts.ui.profile

/** Test tags of the Profile screen. */
object ProfileTags {
    const val BACK = "profile_back"
    const val LOADING = "profile_loading"
    const val EMPTY = "profile_empty"
    const val NO_MATCH_FILTERS = "profile_no_match_filters"
    const val FAILED = "profile_failed"
    const val RETRY = "profile_retry"
    const val NAME = "profile_name"
    const val SINCE = "profile_since"
    const val IGNORED = "profile_ignored"
    const val CONTENT = "profile_content"
    const val HEATMAP_PLACEHOLDER = "profile_heatmap_placeholder"
    const val TREND_CURVE = "profile_trend_curve"
    const val TREND_TOO_FEW = "profile_trend_too_few"

    const val START_SCORE_FILTER = "profile_start_score_filter"
    const val OUT_RULE_FILTER = "profile_out_rule_filter"
    const val VARIANT_FILTER = "profile_variant_filter"
    const val PERIOD_FILTER = "profile_period_filter"

    /** The tab of [section], one of `x01`, `cricket`, `heatmap`. */
    fun tab(section: ProfileSection): String = "profile_tab_${section.name.lowercase()}"

    /** The row of the figure [key] (for example `average`). */
    fun figure(key: String): String = "profile_figure_$key"

    /** The block [key] (for example `trend`). */
    fun block(key: String): String = "profile_block_$key"
}
