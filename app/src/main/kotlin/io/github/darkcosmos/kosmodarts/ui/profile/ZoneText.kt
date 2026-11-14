package io.github.darkcosmos.kosmodarts.ui.profile

import android.content.res.Resources
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.board.heatmapPercentText
import java.util.Locale

private const val PERCENT = 100.0

/** HM6: the name of a zone in the language of [resources]: "Triple 20", "Simple 5", "Bull extérieur (25)", "Bull". */
internal fun ZoneName.text(resources: Resources): String =
    if (number == null) resources.getString(resId) else resources.getString(resId, number)

/** HM6: a dart count with its plural: "1 dart", "12 darts", "0 fléchette". */
internal fun dartsText(resources: Resources, count: Int): String =
    resources.getQuantityString(R.plurals.heatmap_darts, count, count)

/** HM6: the darts and the percentage of a figure, "12 darts · 4.7 %" ("4,7 %" in French). */
internal fun ZoneFigure.detail(resources: Resources, locale: Locale): String =
    resources.getString(
        R.string.heatmap_zone_detail,
        dartsText(resources, count),
        heatmapPercentText(percent / PERCENT, locale),
    )

/** HM6, PS8: the text TalkBack reads for a zone: name, darts and percentage. */
internal fun ZoneFigure.description(resources: Resources, locale: Locale): String =
    resources.getString(
        R.string.heatmap_zone_description,
        name.text(resources),
        dartsText(resources, count),
        heatmapPercentText(percent / PERCENT, locale),
    )
