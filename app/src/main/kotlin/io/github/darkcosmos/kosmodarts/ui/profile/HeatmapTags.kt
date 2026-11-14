package io.github.darkcosmos.kosmodarts.ui.profile

/** Test tags of the Heatmap section (the filters reuse those of [ProfileTags]). */
object HeatmapTags {
    const val MODE_FILTER = "heatmap_mode_filter"
    const val EMPTY = "heatmap_empty"
    const val MISSES = "heatmap_misses"
    const val TOP_LIST = "heatmap_top_list"

    /** The row [index] (from 0) of the top 5 list. */
    fun topRow(index: Int): String = "heatmap_top_$index"
}
