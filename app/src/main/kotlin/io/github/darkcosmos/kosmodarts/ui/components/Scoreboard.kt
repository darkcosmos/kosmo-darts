package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.ui.format.displayName
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.PlayerPalette

private val STRIP = 6.dp
private val RANK_WIDTH = 44.dp
private val SQUARE = 14.dp
private val ROW_MIN_HEIGHT = 52.dp

private const val NAME_MAX_LINES = 2

/** A figure of a [ScoreboardRow]: its text, `accent` for the leg winner's figures, and its test tag. */
class ScoreCell(val text: String, val isAccent: Boolean = false, val tag: String? = null, val width: Dp = NUMBER_WIDTH)

/** Width of a score column; wider columns (the average) pass their own. */
val NUMBER_WIDTH: Dp = 52.dp

/**
 * One player of a [Scoreboard].
 *
 * @property rank shown in a first cell on the end screen (rank 1 on `accent`), or `null` (between legs).
 * @property description what TalkBack reads for the whole row, "Bob, Sets 1, Legs 2, match average 66,80".
 */
class ScoreboardRow(
    val name: String,
    val slot: ColorSlot,
    val rank: Int?,
    val cells: List<ScoreCell>,
    val description: String,
    val tag: String? = null,
)

/**
 * A broadcast-style table (design system, `Scoreboard`; M16, M17): a header line in `label`, then one row per player.
 * Between legs, each row starts with a strip of the player's color; on the end screen, with the rank cell and a
 * square of the player's color before the name. Names are on two lines at most, figures never wrap.
 */
@Composable
fun Scoreboard(
    headers: List<ScoreCell>,
    playerHeader: String,
    rows: List<ScoreboardRow>,
    modifier: Modifier = Modifier,
) {
    val colors = KosmoTheme.colors
    val hasRank = rows.any { it.rank != null }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (hasRank) RANK_WIDTH + 12.dp else STRIP + 12.dp, end = 12.dp)
                .semantics { hideFromAccessibility() },
        ) {
            HeaderText(playerHeader, Modifier.weight(1f), TextAlign.Start)
            headers.forEach { header ->
                HeaderText(header.text, Modifier.width(header.width).let { m ->
                    header.tag?.let { m.testTag(it) } ?: m
                }, TextAlign.End)
            }
        }
        rows.forEach { row -> ScoreRow(row) }
    }
}

/** One player: the strip or the rank cell, the color square (end screen), the name and the figures. */
@Composable
private fun ScoreRow(row: ScoreboardRow) {
    val colors = KosmoTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = ROW_MIN_HEIGHT)
            .clip(KosmoShapes.control)
            .background(colors.surface)
            .let { m -> row.tag?.let { m.testTag(it) } ?: m }
            .semantics(mergeDescendants = true) { contentDescription = row.description },
    ) {
        val rank = row.rank
        if (rank == null) {
            Box(Modifier.width(STRIP).fillMaxHeight().background(PlayerPalette.colorOf(row.slot, colors)))
        } else {
            RankCell(rank)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        ) {
            if (rank != null) {
                Box(Modifier.size(SQUARE).background(PlayerPalette.colorOf(row.slot, colors)))
            }
            Text(
                text = displayName(row.name),
                style = KosmoTheme.typography.playerNameZone,
                color = colors.onBackground,
                maxLines = NAME_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            row.cells.forEach { cell -> NumberCell(cell) }
        }
    }
}

@Composable
private fun HeaderText(text: String, modifier: Modifier, align: TextAlign) {
    Text(
        text = text,
        style = KosmoTheme.typography.label,
        color = KosmoTheme.colors.onBackgroundMuted,
        textAlign = align,
        maxLines = 1,
        modifier = modifier,
    )
}

/** The rank: rank 1 on `accent`, the others on `surfaceHigher` (mockup 09). */
@Composable
private fun RankCell(rank: Int) {
    val colors = KosmoTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(RANK_WIDTH)
            .fillMaxHeight()
            .background(if (rank == 1) colors.accent else colors.surfaceHigher),
    ) {
        Text(
            text = rank.toString(),
            style = KosmoTheme.typography.visit,
            color = if (rank == 1) colors.onAccent else colors.onBackground,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun NumberCell(cell: ScoreCell) {
    val colors = KosmoTheme.colors
    Text(
        text = cell.text,
        style = KosmoTheme.typography.visit,
        color = if (cell.isAccent) colors.accent else colors.onBackground,
        textAlign = TextAlign.End,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .width(cell.width)
            .let { m -> cell.tag?.let { m.testTag(it) } ?: m },
    )
}
