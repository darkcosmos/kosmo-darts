package io.github.darkcosmos.kosmodarts.ui.profile

import android.content.res.Resources
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.format.formatAverage
import io.github.darkcosmos.kosmodarts.ui.format.formatMpr
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val PERCENT = 100

/** ST6: the text of a figure that cannot be computed. */
internal fun noneText(resources: Resources): String = resources.getString(R.string.profile_none)

/** An average with two decimals, or the dash (ST6). */
internal fun averageText(value: Double?, locale: Locale, resources: Resources): String =
    if (value == null) noneText(resources) else formatAverage(value, locale)

/** Marks per round with one decimal, or the dash (ST6). */
internal fun mprText(value: Double?, locale: Locale, resources: Resources): String =
    formatMpr(value, locale, noneText(resources))

/** A whole number, or the dash (ST6). */
internal fun numberText(value: Int?, resources: Resources): String = value?.toString() ?: noneText(resources)

/** A ratio from 0 to 1 as a percentage, or the dash (ST6). */
internal fun percentText(ratio: Double?, locale: Locale, resources: Resources): String =
    if (ratio == null) noneText(resources) else String.format(locale, "%.0f %%", ratio * PERCENT)

/** STX3: "45 % (5/11)", or the dash without any attempt. */
internal fun checkoutText(
    successes: Int,
    attempts: Int,
    ratio: Double?,
    locale: Locale,
    resources: Resources,
): String = if (ratio == null) {
    noneText(resources)
} else {
    resources.getString(R.string.profile_checkout_value, percentText(ratio, locale, resources), successes, attempts)
}

/** A number of matches as a plural, "0 matches", "1 match", "2 matches" (English) or "0 match", "2 matchs" (French). */
internal fun matchCountText(resources: Resources, count: Int): String =
    resources.getQuantityString(R.plurals.profile_match_count, count, count)

/** A number of legs as a plural. */
internal fun legCountText(resources: Resources, count: Int): String =
    resources.getQuantityString(R.plurals.profile_leg_count, count, count)

/** ST10: the notice of the matches ignored because their data is unreadable. */
internal fun ignoredMatchesText(resources: Resources, count: Int): String =
    resources.getQuantityString(R.plurals.profile_ignored_matches, count, count)

/** ST9: "Since always" until a real reset, then "Since <date>" with the date in the format of [locale]. */
internal fun sinceText(resources: Resources, since: ProfileSince, locale: Locale): String = when (since) {
    ProfileSince.Always -> resources.getString(R.string.profile_since_always)
    is ProfileSince.Date -> resources.getString(
        R.string.profile_since_date,
        DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(since.millis)),
    )
}
