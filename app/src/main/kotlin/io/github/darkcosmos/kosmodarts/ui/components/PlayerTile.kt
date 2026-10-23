package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.ui.format.displayName
import io.github.darkcosmos.kosmodarts.ui.format.indicatorDescription
import io.github.darkcosmos.kosmodarts.ui.format.nextPlayerDescription
import io.github.darkcosmos.kosmodarts.ui.format.tileMatchText
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.game.MatchIndicator
import io.github.darkcosmos.kosmodarts.ui.game.VisitBoxText
import io.github.darkcosmos.kosmodarts.ui.layout.TileVariant
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.LABEL_FIGURES
import io.github.darkcosmos.kosmodarts.ui.theme.PlayerPalette

private val STRIP = 4.dp
private val TILE_PADDING = 8.dp
private const val TWO_LINE_NAME_MAX_LINES = 2

/** What a tile shows of another player. */
class TileContent(
    val index: Int,
    val name: String,
    val slot: ColorSlot,
    val remaining: Int,
    val visitBox: VisitBoxText,
    val indicator: MatchIndicator?,
    val isNext: Boolean,
)

/**
 * A player who is not throwing (design system, `PlayerTile`; M6): a 4 dp strip of his color (top in portrait, start
 * in landscape), his name in capitals (ellipsized), his remaining score (`tileScore`, never truncated), his outlined
 * visit box and, in a match, the line "S n · M n". No visible "next" marker (owner Q10): the first tile is the next
 * player, and TalkBack says so (U4).
 */
@Composable
fun PlayerTile(content: TileContent, variant: TileVariant, modifier: Modifier = Modifier) {
    val colors = KosmoTheme.colors
    val strip = PlayerPalette.colorOf(content.slot, colors)
    val isLandscape = variant == TileVariant.COMPACT || variant == TileVariant.COMPACT_TWO_LINES
    val base = modifier
        .clip(KosmoShapes.control)
        .background(colors.surface)
        .testTag(GameTags.tile(content.index))
        .semantics { isTraversalGroup = true }
    if (isLandscape) {
        Row(modifier = base.height(IntrinsicSize.Min)) {
            Box(Modifier.width(STRIP).fillMaxHeight().background(strip))
            TileBody(content, variant, Modifier.weight(1f).padding(TILE_PADDING))
        }
    } else {
        Column(modifier = base) {
            Box(Modifier.fillMaxWidth().height(STRIP).background(strip))
            TileBody(content, variant, Modifier.fillMaxWidth().padding(TILE_PADDING))
        }
    }
}

@Composable
private fun TileBody(content: TileContent, variant: TileVariant, modifier: Modifier) {
    val typography = KosmoTheme.typography
    val scoreStyle = if (variant == TileVariant.NARROW || variant == TileVariant.COMPACT_TWO_LINES) {
        typography.tileScoreSmall
    } else {
        typography.tileScore
    }
    val name: @Composable (Modifier, Int) -> Unit = { nameModifier, maxLines ->
        TileName(content, nameModifier, maxLines)
    }
    val matchLine: @Composable () -> Unit = { content.indicator?.let { MatchLine(it, content.index) } }
    val score: @Composable () -> Unit = {
        Text(
            text = content.remaining.toString(),
            style = scoreStyle,
            color = KosmoTheme.colors.onBackground,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(GameTags.remaining(content.index)),
        )
    }
    val box: @Composable () -> Unit = {
        VisitBox(content.visitBox, content.index, isFilled = false, style = typography.labelLarge)
    }
    when (variant) {
        TileVariant.WIDE -> Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                name(Modifier.weight(1f), 1)
                matchLine()
            }
            ScoreLine(score, box)
        }
        TileVariant.NARROW -> Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // T8: on two lines at most, as in landscape at 4 players, so at least 8 characters show before "…".
            name(Modifier.fillMaxWidth(), TWO_LINE_NAME_MAX_LINES)
            ScoreLine(score, box)
            matchLine()
        }
        TileVariant.COMPACT -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            name(Modifier.weight(1f), 1)
            matchLine()
            box()
            score()
        }
        TileVariant.COMPACT_TWO_LINES -> Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // U3: the name alone on its line, on two lines at most, so at least 8 characters show before "…".
            name(Modifier.fillMaxWidth(), TWO_LINE_NAME_MAX_LINES)
            ScoreLine(score, box)
            matchLine()
        }
    }
}

/**
 * The score at the start and the box at the end, neither ever truncated: on a narrow tile at a large font size, the
 * box goes under the score (VT11).
 */
@Composable
private fun ScoreLine(score: @Composable () -> Unit, box: @Composable () -> Unit) {
    FlowRow(
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        score()
        box()
    }
}

/** The name; on the next player's tile, TalkBack reads "Next player: Anna, 301 points left" (U4). */
@Composable
private fun TileName(content: TileContent, modifier: Modifier, maxLines: Int) {
    val description = if (content.isNext) {
        nextPlayerDescription(LocalResources.current, content.name, content.remaining)
    } else {
        null
    }
    Text(
        text = displayName(content.name),
        style = KosmoTheme.typography.playerNameTile,
        color = KosmoTheme.colors.onBackground,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .testTag(GameTags.playerName(content.index))
            .then(if (description == null) Modifier else Modifier.semantics { contentDescription = description }),
    )
}

/** M14: "S0 · M1" in `label` style, `onBackgroundMuted`; read as "0 sets won, 1 leg won". */
@Composable
private fun MatchLine(indicator: MatchIndicator, index: Int) {
    val description = indicatorDescription(indicator)
    Text(
        text = tileMatchText(indicator),
        style = KosmoTheme.typography.label.copy(fontFeatureSettings = LABEL_FIGURES),
        color = KosmoTheme.colors.onBackgroundMuted,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .testTag(GameTags.matchIndicators(index))
            .semantics { contentDescription = description },
    )
}
