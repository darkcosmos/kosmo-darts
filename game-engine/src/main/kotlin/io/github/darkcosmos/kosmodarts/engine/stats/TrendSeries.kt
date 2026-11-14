package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings

/** The fewest points that draw a trend curve (PS5). */
const val MIN_CURVE_POINTS: Int = 2

/**
 * One point of the trend curve: the value of one match of the profile.
 *
 * @property endedAt the end of the match, milliseconds since the epoch (the date shown for the point).
 * @property value the 3-dart average of an X01 match (STX1) or the MPR of a Cricket match (STC2).
 */
data class TrendPoint(val sessionId: String, val endedAt: Long, val value: Double)

/**
 * The text summary of a trend curve (PS5): what the curve text and TalkBack read instead of the drawing.
 *
 * @property count number of points.
 * @property firstDate end of the first match, `null` without point.
 * @property lastDate end of the last match, `null` without point.
 */
data class TrendSummary(
    val count: Int,
    val first: Double?,
    val last: Double?,
    val lowest: Double?,
    val highest: Double?,
    val firstDate: Long?,
    val lastDate: Long?,
) {
    /** Whether there is at least one point, so the lowest, highest and dates exist (PS5). */
    val hasPoints: Boolean get() = count > 0

    /** Whether the curve is drawn: with fewer than [MIN_CURVE_POINTS] points a short text replaces it (PS5). */
    val hasCurve: Boolean get() = count >= MIN_CURVE_POINTS
}

/**
 * The trend series of [profileId] over the selected [matches] (already validated, ST10): one point per match, in end
 * order (ties by start, then input order), ST7. A match without computable value (no dart of the profile, ST6) gives no
 * point. The value of each match is computed from its precomputed facts, never by replaying.
 */
fun trendSeries(matches: List<StatsMatch>, profileId: Long): List<TrendPoint> =
    matches.sortedWith(compareBy<StatsMatch> { it.endedAt }.thenBy { it.startedAt })
        .mapNotNull { match -> matchValue(match, profileId)?.let { TrendPoint(match.sessionId, match.endedAt, it) } }

private fun matchValue(match: StatsMatch, profileId: Long): Double? = when (match.settings) {
    is ArchivedSettings.X01 -> x01Figures(listOf(match), profileId).average
    is ArchivedSettings.Cricket -> cricketFigures(listOf(match), profileId).mpr
}

/** The [TrendSummary] of [points], which are in end order (ST7, PS5). */
fun summarize(points: List<TrendPoint>): TrendSummary = TrendSummary(
    count = points.size,
    first = points.firstOrNull()?.value,
    last = points.lastOrNull()?.value,
    lowest = points.minOfOrNull { it.value },
    highest = points.maxOfOrNull { it.value },
    firstDate = points.firstOrNull()?.endedAt,
    lastDate = points.lastOrNull()?.endedAt,
)
