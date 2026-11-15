package io.github.darkcosmos.kosmodarts.ui.headtohead

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.stats.CricketFigures
import io.github.darkcosmos.kosmodarts.engine.stats.HeadToHeadFigures
import io.github.darkcosmos.kosmodarts.engine.stats.X01Figures
import io.github.darkcosmos.kosmodarts.ui.components.SectionLabel
import io.github.darkcosmos.kosmodarts.ui.components.WordSafeText
import io.github.darkcosmos.kosmodarts.ui.profile.FigureRow
import io.github.darkcosmos.kosmodarts.ui.profile.FigureRowView
import io.github.darkcosmos.kosmodarts.ui.profile.averageText
import io.github.darkcosmos.kosmodarts.ui.profile.checkoutText
import io.github.darkcosmos.kosmodarts.ui.profile.mprText
import io.github.darkcosmos.kosmodarts.ui.profile.numberText
import io.github.darkcosmos.kosmodarts.ui.profile.profileLocale
import io.github.darkcosmos.kosmodarts.ui.profile.profileResources
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import java.util.Locale

/** A figure of both players: [a] at the left, [b] at the right; "–" when it cannot be computed (ST6). */
internal data class CompareRow(val key: String, val label: String, val a: String, val b: String)

/** A titled group: [singles] are one value for both players (the common count, the draws), [rows] are side by side. */
internal data class CompareBlock(
    val key: String,
    val title: String,
    val rows: List<CompareRow>,
    val singles: List<FigureRow> = emptyList(),
)

/** HH3, HH4: the counters of the common matches; the draws are apart from the wins (owner Q1). */
@Composable
internal fun resultsBlock(figures: HeadToHeadFigures): CompareBlock = CompareBlock(
    key = "results",
    title = stringResource(R.string.h2h_block_results),
    singles = listOf(
        FigureRow("common", stringResource(R.string.h2h_common_matches), figures.commonCount.toString()),
        FigureRow("draws", stringResource(R.string.h2h_draws), figures.draws.toString()),
    ),
    rows = listOf(
        CompareRow("wins", stringResource(R.string.h2h_wins), figures.winsA.toString(), figures.winsB.toString()),
        CompareRow(
            "legs",
            stringResource(R.string.h2h_legs_won),
            figures.legsWonA.toString(),
            figures.legsWonB.toString(),
        ),
    ),
)

/** HH5, STX1 to STX4: the X01 figures of both players over the common matches. */
@Composable
internal fun x01Block(figures: HeadToHeadFigures): CompareBlock {
    val resources = profileResources()
    val locale = profileLocale()
    val a = figures.x01A
    val b = figures.x01B
    return CompareBlock(
        key = "x01",
        title = stringResource(R.string.profile_tab_x01),
        rows = listOf(
            CompareRow(
                "average",
                stringResource(R.string.profile_average),
                averageText(a.average, locale, resources),
                averageText(b.average, locale, resources),
            ),
            CompareRow(
                "first_nine",
                stringResource(R.string.profile_first_nine_average),
                averageText(a.firstNineAverage, locale, resources),
                averageText(b.firstNineAverage, locale, resources),
            ),
            CompareRow(
                "checkout_rate",
                stringResource(R.string.profile_checkout_rate),
                a.checkoutValue(locale, resources),
                b.checkoutValue(locale, resources),
            ),
            CompareRow(
                "best_checkout",
                stringResource(R.string.profile_best_checkout),
                numberText(a.bestCheckout, resources),
                numberText(b.bestCheckout, resources),
            ),
        ),
    )
}

private fun X01Figures.checkoutValue(locale: Locale, resources: Resources): String =
    checkoutText(checkoutSuccesses, checkoutAttempts, checkoutRatio, locale, resources)

/** HH5, STC2: the Cricket marks per round of both players over the common matches. */
@Composable
internal fun cricketBlock(figures: HeadToHeadFigures): CompareBlock {
    val resources = profileResources()
    val locale = profileLocale()
    val a: CricketFigures = figures.cricketA
    val b: CricketFigures = figures.cricketB
    return CompareBlock(
        key = "cricket",
        title = stringResource(R.string.cricket_title),
        rows = listOf(
            CompareRow(
                "mpr",
                stringResource(R.string.profile_mpr),
                mprText(a.mpr, locale, resources),
                mprText(b.mpr, locale, resources),
            ),
        ),
    )
}

/** The blocks the mode shows (HH6): All modes shows the results, then X01 and Cricket apart. */
@Composable
internal fun compareBlocks(result: HeadToHeadResult, mode: HeadToHeadMode): List<CompareBlock> {
    val figures = result.figures
    return listOfNotNull(
        resultsBlock(figures),
        if (mode != HeadToHeadMode.CRICKET) x01Block(figures) else null,
        if (mode != HeadToHeadMode.X01) cricketBlock(figures) else null,
    )
}

/** One block: its title, the names of both players above their values, the single values, the side-by-side rows. */
@Composable
internal fun CompareBlockView(block: CompareBlock, nameA: String, nameB: String, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(HeadToHeadTags.block(block.key)),
    ) {
        SectionLabel(block.title)
        block.singles.forEach { FigureRowView(it.copy(key = "h2h_${it.key}")) }
        NamesRow(block.key, nameA, nameB)
        block.rows.forEach { CompareRowView(it, nameA, nameB) }
    }
}

/** The two names in two equal columns: a long name wraps at its spaces and shrinks rather than being cut (U11). */
@Composable
private fun NamesRow(key: String, nameA: String, nameB: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("a" to nameA, "b" to nameB).forEach { (side, name) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .testTag(HeadToHeadTags.name(key, side)),
            ) {
                WordSafeText(text = name, style = KosmoTheme.typography.body.copy(color = KosmoTheme.colors.accent))
            }
        }
    }
}

/** A figure side by side: its label, then the value of A at the left and of B at the right (one TalkBack element). */
@Composable
private fun CompareRowView(row: CompareRow, nameA: String, nameB: String) {
    val colors = KosmoTheme.colors
    val description = stringResource(R.string.h2h_row_description, row.label, nameA, row.a, nameB, row.b)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = description
                testTag = HeadToHeadTags.figure(row.key)
            },
    ) {
        Text(
            text = row.label,
            style = KosmoTheme.typography.body,
            color = colors.onBackgroundMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = row.a,
                style = KosmoTheme.typography.bodyLarge,
                color = colors.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = row.b,
                style = KosmoTheme.typography.bodyLarge,
                color = colors.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
