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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor
import io.github.darkcosmos.kosmodarts.ui.format.displayName
import io.github.darkcosmos.kosmodarts.ui.format.indicatorDescription
import io.github.darkcosmos.kosmodarts.ui.format.nextPlayerDescription
import io.github.darkcosmos.kosmodarts.ui.format.tileMatchText
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.game.MatchIndicator
import io.github.darkcosmos.kosmodarts.ui.game.VisitBoxText
import io.github.darkcosmos.kosmodarts.ui.layout.GameLayoutPolicy
import io.github.darkcosmos.kosmodarts.ui.layout.TileVariant
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTypography
import io.github.darkcosmos.kosmodarts.ui.theme.LABEL_FIGURES
import io.github.darkcosmos.kosmodarts.ui.theme.PlayerPalette
import io.github.darkcosmos.kosmodarts.ui.theme.fixedSp

private val STRIP = 4.dp
private val TILE_PADDING = 8.dp
private const val TWO_LINE_NAME_MAX_LINES = 2
private const val NO_WIDTH = -1

/** What a tile shows of another player. */
class TileContent(
    val index: Int,
    val name: String,
    val color: PlayerColor,
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
    val strip = PlayerPalette.colorOf(content.color)
    val isLandscape = variant == TileVariant.COMPACT || variant == TileVariant.COMPACT_TWO_LINES
    val base = modifier
        .clip(KosmoShapes.control)
        .background(colors.surface)
        .testTag(GameTags.tile(content.index))
        .semantics { isTraversalGroup = true }
    if (isLandscape) {
        Row(modifier = base.height(IntrinsicSize.Min)) {
            Box(Modifier.width(STRIP).fillMaxHeight().background(strip))
            TileBody(
                content,
                variant,
                Modifier
                    .weight(1f)
                    .padding(horizontal = TILE_PADDING, vertical = GameLayoutPolicy.TILE_PADDING_LANDSCAPE_VERTICAL.dp),
            )
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
        TileName(content, variant, nameModifier, maxLines)
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
            // T8: two lines at most, only for a name of several words; no break inside a word (#70, VT11 as amended).
            name(Modifier.fillMaxWidth(), TWO_LINE_NAME_MAX_LINES)
            ScoreLine(score, box)
            matchLine()
        }
        TileVariant.COMPACT, TileVariant.COMPACT_TWO_LINES -> Column(
            modifier,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // U3, U19: the name on two lines at most (only for several words, never broken inside a word, #70), never
            // sharing its line with the score and the box; the match line beside it keeps the tile on two lines (owner
            // review of PR #57: lower tiles in landscape).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                name(Modifier.weight(1f), TWO_LINE_NAME_MAX_LINES)
                matchLine()
            }
            ScoreLine(score, box)
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

/**
 * The name, never broken inside a word (#70, VT11, U3, U11): it shrinks from the user's size down to its size at font
 * scale 1.0, then ends with "…". Two lines only when the variant allows them and the name has two words; a one-word
 * name stays on one line. On the next player's tile, TalkBack reads "Next player: Anna, 301 points left" (U4).
 */
@Composable
private fun TileName(content: TileContent, variant: TileVariant, modifier: Modifier, maxLines: Int) {
    val description = if (content.isNext) {
        nextPlayerDescription(LocalResources.current, content.name, content.remaining)
    } else {
        null
    }
    val text = displayName(content.name)
    val style = KosmoTheme.typography.playerNameTile
    val density = LocalDensity.current
    val autoSize = remember(style.fontSize, density.density, density.fontScale) {
        // At font scale 1.0 (and below) the minimum equals the maximum: the size does not change.
        val fixed = density.fixedSp(KosmoTypography.PLAYER_NAME_TILE)
        val smallest = with(density) { if (fixed.toPx() < style.fontSize.toPx()) fixed else style.fontSize }
        WordSafeAutoSize(smallest, style.fontSize)
    }
    // A multi-word name can still have a word wider than its line, even at the minimum size (e.g. "MAXIME DUPONT" in a
    // narrow landscape tile): the second line would then break that word. The layout reports it here, and this flag
    // (the width of that layout) puts the name on one line, ending with "…": one extra composition, rare. It resets
    // when the name, the variant or the font scale changes, and the layout clears it when its width changes (rotation).
    var oneLineAtWidth by remember(text, variant, density.fontScale) { mutableIntStateOf(NO_WIDTH) }
    val twoLinesAllowed = maxLines > 1 && wordCount(text) > 1 && oneLineAtWidth == NO_WIDTH
    Text(
        text = text,
        style = style,
        color = KosmoTheme.colors.onBackground,
        maxLines = if (twoLinesAllowed) maxLines else 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = autoSize,
        onTextLayout = { layout ->
            val width = layout.layoutInput.constraints.maxWidth
            if (layout.lineCount > 1 && !breaksOnlyBetweenWords(text, layout.lineEnds())) {
                oneLineAtWidth = width
            } else if (oneLineAtWidth != NO_WIDTH && oneLineAtWidth != width) {
                oneLineAtWidth = NO_WIDTH
            }
        },
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
