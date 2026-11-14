package io.github.darkcosmos.kosmodarts.ui.profile

import io.github.darkcosmos.kosmodarts.engine.stats.TrendPoint
import io.github.darkcosmos.kosmodarts.engine.stats.summarize

/** Trend points for the Profile tests: [values] one per day from 2026-03-01 at noon UTC (the same day everywhere). */
internal object TrendTestData {
    private const val FIRST_DAY = 1_772_366_400_000L
    private const val DAY = 86_400_000L

    fun points(values: List<Double>): List<TrendPoint> =
        values.mapIndexed { index, value -> TrendPoint("m$index", FIRST_DAY + index * DAY, value) }

    /** [count] values going up and down around [base] by [amplitude], so the curve has a visible shape. */
    fun wave(count: Int, base: Double = 55.0, amplitude: Double = 20.0): List<Double> =
        List(count) { base + amplitude * kotlin.math.sin(it * 0.9) }

    /** [state] with the trend of its X01 and Cricket selections replaced by [x01] and [cricket] (and summaries). */
    fun withTrend(state: ProfileState, x01: List<TrendPoint>, cricket: List<TrendPoint>): ProfileState {
        val loaded = state.content as ProfileContent.Loaded
        val figures = requireNotNull(loaded.figures)
        return state.copy(
            content = loaded.copy(
                figures = figures.copy(
                    x01 = figures.x01.copy(trend = x01, trendSummary = summarize(x01)),
                    cricket = figures.cricket.copy(trend = cricket, trendSummary = summarize(cricket)),
                ),
            ),
        )
    }
}
