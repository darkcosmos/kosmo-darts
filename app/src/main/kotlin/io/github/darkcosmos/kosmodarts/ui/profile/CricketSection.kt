package io.github.darkcosmos.kosmodarts.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketTarget
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.stats.CricketFigures
import io.github.darkcosmos.kosmodarts.engine.stats.CricketResults
import java.util.Locale

/**
 * The Cricket section (PS4, STC2 to STC5): the figures that follow the filter, then one results block per variant
 * ("All variants" gives two). The points blocks only show for the variant they belong to; a figure that cannot be
 * computed is "–" (ST6).
 */
@Composable
internal fun cricketBlocks(selection: CricketSelection, variant: CricketVariant?): List<FigureBlock> {
    val resources = profileResources()
    val locale = profileLocale()
    val figures = selection.figures
    val blocks = mutableListOf(
        FigureBlock(
            "averages",
            stringResource(R.string.profile_block_averages),
            listOf(
                FigureRow("mpr", stringResource(R.string.profile_mpr), mprText(figures.mpr, locale, resources)),
                FigureRow(
                    "best_visit",
                    stringResource(R.string.profile_best_visit_marks),
                    numberText(figures.bestVisit, resources),
                ),
            ),
        ),
        FigureBlock(
            "marks",
            stringResource(R.string.profile_block_marks),
            CricketTarget.entries.map { target ->
                FigureRow(
                    "marks_${target.name.lowercase()}",
                    targetLabel(target),
                    (figures.marksPerTarget[target] ?: 0).toString(),
                )
            },
        ),
    )
    pointsBlock(figures, variant, locale)?.let { blocks += it }
    figures.results.forEach { blocks += resultsBlock(it) }
    blocks += trendBlock(selection.matchCount)
    return blocks
}

@Composable
private fun targetLabel(target: CricketTarget): String =
    if (target.isNumber) target.points.toString() else stringResource(R.string.profile_target_bull)

/** STC4: points scored in Standard, points given in Cut-throat; both with "All variants". */
@Composable
private fun pointsBlock(figures: CricketFigures, variant: CricketVariant?, locale: Locale): FigureBlock? {
    val resources = profileResources()
    val rows = mutableListOf<FigureRow>()
    if (variant != CricketVariant.CUT_THROAT) {
        rows += FigureRow(
            "points_scored",
            stringResource(R.string.profile_points_scored),
            numberText(figures.pointsScored, resources),
        )
        rows += FigureRow(
            "points_scored_leg",
            stringResource(R.string.profile_points_scored_leg),
            averageText(figures.pointsScoredPerLeg, locale, resources),
        )
    }
    if (variant != CricketVariant.STANDARD) {
        rows += FigureRow(
            "points_given",
            stringResource(R.string.profile_points_given),
            numberText(figures.pointsGiven, resources),
        )
        rows += FigureRow(
            "points_given_leg",
            stringResource(R.string.profile_points_given_leg),
            averageText(figures.pointsGivenPerLeg, locale, resources),
        )
    }
    return if (rows.isEmpty()) null else FigureBlock("points", stringResource(R.string.profile_block_points), rows)
}

/** STC5: the results of one variant (solo matches excluded by the engine, ST5). */
@Composable
private fun resultsBlock(results: CricketResults): FigureBlock {
    val resources = profileResources()
    val locale = profileLocale()
    val variantLabel = when (results.variant) {
        CricketVariant.STANDARD -> stringResource(R.string.cricket_variant_standard)
        CricketVariant.CUT_THROAT -> stringResource(R.string.cricket_variant_cut_throat)
    }
    val key = results.variant.name.lowercase()
    return FigureBlock(
        "results_$key",
        stringResource(R.string.profile_block_results_variant, variantLabel),
        listOf(
            FigureRow(
                "${key}_matches_won",
                stringResource(R.string.profile_matches_won),
                matchCountText(resources, results.matchesWon),
            ),
            FigureRow(
                "${key}_matches_lost",
                stringResource(R.string.profile_matches_lost),
                matchCountText(resources, results.matchesLost),
            ),
            FigureRow(
                "${key}_win_rate",
                stringResource(R.string.profile_win_rate),
                percentText(results.winRatio, locale, resources),
            ),
            FigureRow(
                "${key}_legs_won",
                stringResource(R.string.profile_legs_won),
                legCountText(resources, results.legsWon),
            ),
            FigureRow("${key}_sets_won", stringResource(R.string.profile_sets_won), results.setsWon.toString()),
        ),
    )
}
