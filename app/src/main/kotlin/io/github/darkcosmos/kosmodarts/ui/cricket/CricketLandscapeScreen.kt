package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.components.Chips
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTypography
import io.github.darkcosmos.kosmodarts.ui.theme.LocalKosmoTypography
import io.github.darkcosmos.kosmodarts.ui.theme.Spacing
import kotlin.math.max

/** The title is read before the board (CR23): a traversal index under the board's 0. */
private const val TITLE_TRAVERSAL_INDEX = -1f

private val TITLE_CHIPS_GAP = 8.dp

/**
 * Landscape (CR22, plan section 4): the board on the left, the title over the single-line zone on the right, the
 * action bar at the bottom of that column. The board, the title and the zone use fixed text sizes (they ignore the font
 * scale, so the rows stay at 48 dp or more); the action bar keeps the scaled `button` style. The padding is the one of
 * the caller (the bars' and cutout insets, see the system bars task), [screenWidthDp] and [screenHeightDp] are what
 * is left inside it.
 */
@Composable
internal fun LandscapeGame(
    game: CricketGameScreenState,
    onEvent: (AppEvent) -> Unit,
    screenWidthDp: Float,
    screenHeightDp: Float,
) {
    val outerDensity = LocalDensity.current
    val outerTypography = KosmoTheme.typography
    FixedTextSizes {
        val typography = KosmoTheme.typography
        val titleLine = max(rememberLineHeightDp(typography.label), chipsHeightDp(game, typography))
        val players = game.game.config.players.size
        val header = rememberHeaderMetrics(
            game.headerTotals,
            CricketBoardLayoutPolicy.PLAYER_COLUMN_WIDTH,
            CricketBoardLayoutPolicy.LANDSCAPE_TOTAL_SIZES_SP,
        )
        val layout = CricketBoardLayoutPolicy.landscape(
            CricketLandscapeSpace(
                screenWidthDp, screenHeightDp, 0f, header.heightDp, titleLine,
                CricketBoardLayoutPolicy.LANDSCAPE_ZONE_LINE, players, game.game.config.targets.size,
            ),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(CricketBoardLayoutPolicy.BOARD_GAP.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag(CricketTags.SCREEN)
                .padding(
                    horizontal = Spacing.screenHorizontal,
                    vertical = CricketBoardLayoutPolicy.LANDSCAPE_MARGIN.dp,
                ),
        ) {
            Column(Modifier.width(layout.boardWidthDp.dp).fillMaxHeight()) {
                Board(game, layout.board, header, onEvent)
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().height(layout.bandHeightDp.dp),
                ) {
                    TitleRow(game, titleLine)
                    Zone(
                        game,
                        Modifier.height(CricketBoardLayoutPolicy.LANDSCAPE_ZONE_LINE.dp).fillMaxWidth(),
                        isSingleLine = true,
                    )
                }
                Spacer(Modifier.weight(1f))
                CompositionLocalProvider(
                    LocalDensity provides outerDensity,
                    LocalKosmoTypography provides outerTypography,
                ) {
                    Actions(game, onEvent)
                }
            }
        }
    }
}

/** "CRICKET · STANDARD", the M14 position, and the S/M chips at the end of the line (Q4 A, Q11 A). */
@Composable
private fun TitleRow(game: CricketGameScreenState, heightDp: Float) {
    val index = game.highlightedPlayerIndex ?: 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .testTag(CricketTags.TITLE)
            .semantics {
                isTraversalGroup = true
                traversalIndex = TITLE_TRAVERSAL_INDEX
            },
    ) {
        Header(game, Modifier.weight(1f), isShortPosition = true)
        game.indicators?.get(index)?.let {
            Spacer(Modifier.width(TITLE_CHIPS_GAP))
            // The chips use the 14 sp label size here, so that the title line stays as low as the board header's name.
            val typography = KosmoTheme.typography
            CompositionLocalProvider(LocalKosmoTypography provides typography.copy(labelLarge = typography.label)) {
                Chips(it, index)
            }
        }
    }
}

/** The height of the S/M chips (their text line and their padding), or 0 when the game has none. */
@Composable
private fun chipsHeightDp(game: CricketGameScreenState, typography: KosmoTypography): Float =
    if (game.indicators?.get(game.highlightedPlayerIndex ?: 0) == null) {
        0f
    } else {
        rememberLineHeightDp(typography.label) + CricketBoardLayoutPolicy.CHIPS_VERTICAL_PADDING
    }

/** Texts that ignore the system font size: the density keeps its scale at 1 and the typography is rebuilt for it. */
@Composable
private fun FixedTextSizes(content: @Composable () -> Unit) {
    val system = LocalDensity.current
    val fixed = remember(system) { Density(system.density, fontScale = 1f) }
    val typography = remember(fixed) { KosmoTypography.create(fixed) }
    CompositionLocalProvider(
        LocalDensity provides fixed,
        LocalKosmoTypography provides typography,
        content = content,
    )
}
