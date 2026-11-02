package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.game.ArchiveFailedMessage
import io.github.darkcosmos.kosmodarts.ui.game.rememberArchiveMessageHeightDp
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTypography
import io.github.darkcosmos.kosmodarts.ui.theme.LocalKosmoTypography
import io.github.darkcosmos.kosmodarts.ui.theme.Spacing


/**
 * Landscape (CR22, plan R2.2): the top band from the top edge (title and the current player's single-line zone), then
 * the board over the whole width; "Undo" right of "Miss" in the header row, and the tall next key right of the rows
 * after the 12 dp gap. Every text is fixed (it ignores the font scale), so the rows keep 42 dp or more (Kosmo, Q14 B).
 * The bars are hidden and the caller pads the display cutout only ([SystemBarsPolicy]): [screenWidthDp] and
 * [screenHeightDp] are what is left inside that padding, [cutoutInsetDp] its left plus right width.
 */
@Composable
internal fun LandscapeGame(
    game: CricketGameScreenState,
    onEvent: (AppEvent) -> Unit,
    screenWidthDp: Float,
    screenHeightDp: Float,
    cutoutInsetDp: Float,
) {
    FixedTextSizes {
        val players = game.game.config.players.size
        // The policy takes the whole screen width and removes the cutout from it.
        val fullWidth = screenWidthDp + cutoutInsetDp
        val header = rememberHeaderMetrics(
            game.headerTotals,
            CricketBoardLayoutPolicy.landscapePlayerColumnWidthDp(fullWidth, cutoutInsetDp, players),
            CricketBoardLayoutPolicy.LANDSCAPE_TOTAL_SIZES_SP,
        )
        // AR3: the failure message takes a line of its own between the band and the board; the board gets the rest.
        val messageHeightDp = rememberArchiveMessageHeightDp(
            game.archive,
            screenWidthDp - 2 * Spacing.screenHorizontal.value,
        )
        val layout = CricketBoardLayoutPolicy.landscapeBand(
            CricketLandscapeBandSpace(
                fullWidth,
                screenHeightDp - messageHeightDp,
                cutoutInsetDp,
                header.heightDp,
                players,
                game.game.config.targets.size,
            ),
        )
        val (undo, next) = actionsOf(game, onEvent)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag(CricketTags.SCREEN)
                .padding(
                    start = Spacing.screenHorizontal,
                    end = Spacing.screenHorizontal,
                    bottom = CricketBoardLayoutPolicy.LANDSCAPE_BOTTOM_MARGIN.dp,
                ),
        ) {
            CricketTopBand(game)
            ArchiveFailedMessage(game.archive)
            Board(
                game,
                layout.board,
                header,
                onEvent,
                modifier = Modifier.fillMaxWidth(),
                actions = CricketBoardActions(undo, next, layout.actionColumnWidthDp, layout.nextKeyHeightDp),
            )
        }
    }
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
