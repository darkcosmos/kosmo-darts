package io.github.darkcosmos.kosmodarts.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.stats.TrendPoint
import io.github.darkcosmos.kosmodarts.engine.stats.TrendSummary
import io.github.darkcosmos.kosmodarts.engine.stats.X01Figures

/** The X01 section (PS4, STX1 to STX8): its blocks of figures, "–" for a figure that cannot be computed (ST6). */
@Composable
internal fun x01Blocks(selection: X01Selection): List<FigureBlock> {
    val figures = selection.figures
    return listOf(
        averagesBlock(figures),
        checkoutsBlock(figures),
        visitsBlock(figures),
        legsBlock(figures),
        resultsBlock(figures),
        trendBlock(selection.trend, selection.trendSummary, isCricket = false),
    )
}

@Composable
private fun averagesBlock(figures: X01Figures): FigureBlock {
    val resources = profileResources()
    val locale = profileLocale()
    return FigureBlock(
        "averages",
        stringResource(R.string.profile_block_averages),
        listOf(
            FigureRow(
                "average",
                stringResource(R.string.profile_average),
                averageText(figures.average, locale, resources),
            ),
            FigureRow(
                "first_nine",
                stringResource(R.string.profile_first_nine_average),
                averageText(figures.firstNineAverage, locale, resources),
            ),
        ),
    )
}

@Composable
private fun checkoutsBlock(figures: X01Figures): FigureBlock {
    val resources = profileResources()
    val locale = profileLocale()
    return FigureBlock(
        "checkouts",
        stringResource(R.string.profile_block_checkouts),
        listOf(
            FigureRow(
                "checkout_rate",
                stringResource(R.string.profile_checkout_rate),
                checkoutText(
                    figures.checkoutSuccesses,
                    figures.checkoutAttempts,
                    figures.checkoutRatio,
                    locale,
                    resources,
                ),
            ),
            FigureRow(
                "best_checkout",
                stringResource(R.string.profile_best_checkout),
                numberText(figures.bestCheckout, resources),
            ),
        ),
    )
}

@Composable
private fun visitsBlock(figures: X01Figures): FigureBlock {
    val resources = profileResources()
    return FigureBlock(
        "visits",
        stringResource(R.string.profile_block_visits),
        listOf(
            FigureRow("visits_180", stringResource(R.string.profile_visits_180), figures.visits180.toString()),
            FigureRow("visits_140", stringResource(R.string.profile_visits_140), figures.visits140.toString()),
            FigureRow("visits_100", stringResource(R.string.profile_visits_100), figures.visits100.toString()),
            FigureRow("visits_60", stringResource(R.string.profile_visits_60), figures.visits60.toString()),
            FigureRow(
                "best_visit",
                stringResource(R.string.profile_best_visit),
                numberText(figures.bestVisit, resources),
            ),
            FigureRow("busts", stringResource(R.string.profile_busts), figures.busts.toString()),
        ),
    )
}

@Composable
private fun legsBlock(figures: X01Figures): FigureBlock {
    val resources = profileResources()
    val locale = profileLocale()
    return FigureBlock(
        "legs",
        stringResource(R.string.profile_block_legs),
        listOf(
            FigureRow(
                "darts_per_leg",
                stringResource(R.string.profile_darts_per_leg),
                averageText(figures.averageDartsPerLegWon, locale, resources),
            ),
            FigureRow(
                "best_leg",
                stringResource(R.string.profile_best_leg),
                numberText(figures.bestLeg, resources),
            ),
        ),
    )
}

@Composable
private fun resultsBlock(figures: X01Figures): FigureBlock {
    val resources = profileResources()
    val locale = profileLocale()
    return FigureBlock(
        "results",
        stringResource(R.string.profile_block_results),
        listOf(
            FigureRow(
                "matches_won",
                stringResource(R.string.profile_matches_won),
                matchCountText(resources, figures.matchesWon),
            ),
            FigureRow(
                "matches_lost",
                stringResource(R.string.profile_matches_lost),
                matchCountText(resources, figures.matchesLost),
            ),
            FigureRow(
                "win_rate",
                stringResource(R.string.profile_win_rate),
                percentText(figures.winRatio, locale, resources),
            ),
            FigureRow(
                "legs_won",
                stringResource(R.string.profile_legs_won),
                legCountText(resources, figures.legsWon),
            ),
            FigureRow("sets_won", stringResource(R.string.profile_sets_won), figures.setsWon.toString()),
        ),
    )
}

/**
 * PS5: the trend block: the number of matches, the curve (or a short text under 2 points) and, as text, the lowest and
 * highest values and the dates of the first and last matches, all read from the engine [summary].
 */
@Composable
internal fun trendBlock(points: List<TrendPoint>, summary: TrendSummary, isCricket: Boolean): FigureBlock {
    val resources = profileResources()
    val locale = profileLocale()
    fun value(v: Double?) = trendValueText(v, isCricket, locale, resources)
    val rows = mutableListOf(
        FigureRow(
            "trend_matches",
            stringResource(R.string.profile_trend_matches),
            matchCountText(resources, summary.count),
        ),
    )
    if (summary.hasPoints) {
        rows += FigureRow("trend_lowest", stringResource(R.string.profile_trend_lowest), value(summary.lowest))
        rows += FigureRow("trend_highest", stringResource(R.string.profile_trend_highest), value(summary.highest))
        rows += FigureRow(
            "trend_first_date",
            stringResource(R.string.profile_trend_first_date),
            trendDateText(summary.firstDate, locale, resources),
        )
        rows += FigureRow(
            "trend_last_date",
            stringResource(R.string.profile_trend_last_date),
            trendDateText(summary.lastDate, locale, resources),
        )
    }
    return FigureBlock(
        "trend",
        stringResource(R.string.profile_block_trend),
        rows,
        note = if (!summary.hasCurve) stringResource(R.string.profile_trend_too_few) else null,
        trend = TrendDisplay(points, summary, trendDescription(summary, isCricket, locale, resources)),
    )
}
