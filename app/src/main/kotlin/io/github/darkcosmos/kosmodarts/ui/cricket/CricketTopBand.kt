package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.ui.components.Chips
import io.github.darkcosmos.kosmodarts.ui.format.positionText
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.LocalKosmoTypography

/** The band is read before the board (CR23, R2.2): a traversal index under the board's 0. */
private const val BAND_TRAVERSAL_INDEX = -1f

/** Vertical padding of the band: its 44 dp keep 36 dp for the title and the zone. */
private val BAND_PADDING_VERTICAL = 4.dp

/** Between the title's texts and the chips, and between the title and the zone. */
private val TITLE_GAP = 8.dp
private val BAND_GAP = 12.dp

/**
 * The landscape top band (R2.2, Kosmo Q14 B and Q15 A), from the top edge over the whole width, one line of fixed
 * texts: "CRICKET · STANDARD", the short position "S2 · M3" and the S/M chips, then the current player's single-line
 * zone (name, MPR or "VICTOIRE", the three dart cells at the right end), which takes the width the title leaves.
 */
@Composable
internal fun CricketTopBand(game: CricketGameScreenState, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BAND_GAP),
        modifier = modifier
            .fillMaxWidth()
            .height(CricketBoardLayoutPolicy.LANDSCAPE_BAND.dp)
            .testTag(CricketTags.BAND)
            .semantics {
                isTraversalGroup = true
                traversalIndex = BAND_TRAVERSAL_INDEX
            }
            .padding(vertical = BAND_PADDING_VERTICAL),
    ) {
        TitleRow(game)
        Zone(game, Modifier.weight(1f).fillMaxHeight(), isSingleLine = true)
    }
}

/**
 * The title at the start of the band: mode, short position (M14), then the S/M chips at the 14 sp label size (Q4 A).
 * It keeps the width of its texts, so the zone gets the rest.
 */
@Composable
private fun TitleRow(game: CricketGameScreenState) {
    val typography = KosmoTheme.typography
    val color = KosmoTheme.colors.onBackgroundMuted
    val index = game.highlightedPlayerIndex ?: 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TITLE_GAP),
        modifier = Modifier
            .testTag(CricketTags.TITLE)
            .semantics { isTraversalGroup = true },
    ) {
        Text(
            text = headerMode(game),
            style = typography.label,
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(GameTags.HEADER_MODE),
        )
        positionText(game.headerPosition, isShort = true)?.let {
            Text(
                text = it,
                style = typography.label,
                color = color,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag(GameTags.HEADER_POSITION),
            )
        }
        game.indicators?.get(index)?.let {
            CompositionLocalProvider(LocalKosmoTypography provides typography.copy(labelLarge = typography.label)) {
                Chips(it, index)
            }
        }
    }
}
