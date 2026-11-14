package io.github.darkcosmos.kosmodarts.ui.profile

import android.content.res.Resources
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.stats.TrendSummary
import java.util.Locale

/** A trend value: the MPR of a Cricket match (one decimal) or the 3-dart average of an X01 match, or the dash (ST6). */
internal fun trendValueText(value: Double?, isCricket: Boolean, locale: Locale, resources: Resources): String =
    if (isCricket) mprText(value, locale, resources) else averageText(value, locale, resources)

/** A date of the summary in the locale format, or the dash without date. */
internal fun trendDateText(millis: Long?, locale: Locale, resources: Resources): String =
    if (millis == null) noneText(resources) else dateText(millis, locale)

/**
 * PS5: what TalkBack reads instead of the drawing: the number of matches, the first, last, lowest and highest values
 * and the dates of the first and last matches, all from [summary] (never computed here).
 */
internal fun trendDescription(
    summary: TrendSummary,
    isCricket: Boolean,
    locale: Locale,
    resources: Resources,
): String {
    if (summary.count == 0) return resources.getString(R.string.profile_trend_description_empty)
    fun value(v: Double?) = trendValueText(v, isCricket, locale, resources)
    return resources.getQuantityString(
        R.plurals.profile_trend_description,
        summary.count,
        summary.count,
        value(summary.first),
        value(summary.last),
        value(summary.lowest),
        value(summary.highest),
        trendDateText(summary.firstDate, locale, resources),
        trendDateText(summary.lastDate, locale, resources),
    )
}
