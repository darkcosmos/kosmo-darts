package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.NoVoiceNotice
import io.github.darkcosmos.kosmodarts.ui.components.ActionBar
import io.github.darkcosmos.kosmodarts.ui.components.BarAction
import io.github.darkcosmos.kosmodarts.ui.components.MatchHeader
import io.github.darkcosmos.kosmodarts.ui.components.ZoneStat
import io.github.darkcosmos.kosmodarts.ui.format.formatMpr
import io.github.darkcosmos.kosmodarts.ui.format.positionText
import io.github.darkcosmos.kosmodarts.ui.game.AbandonDialog
import io.github.darkcosmos.kosmodarts.ui.game.ArchiveFailedMessage
import io.github.darkcosmos.kosmodarts.ui.game.KeypadTags
import io.github.darkcosmos.kosmodarts.ui.game.NoVoiceDialog
import io.github.darkcosmos.kosmodarts.ui.game.confirmAction
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.Spacing
import io.github.darkcosmos.kosmodarts.ui.theme.States

/**
 * Cricket game screen (docs/rules/cricket.md, CR22 to CR25). Portrait, top to bottom: header, compact zone, the board
 * with its buttons, the action bar. Landscape ([LandscapeGame]): the top band (title, single-line zone), then the board
 * over the whole width with "Undo" and the tall next key at its right end. The sizes come from
 * [CricketBoardLayoutPolicy], from values the screen measures with Compose. The screen
 * stays on, and system back opens "Abandon the game?" (M15).
 */
@Composable
fun CricketGameScreen(
    game: CricketGameScreenState,
    onEvent: (AppEvent) -> Unit,
    modifier: Modifier = Modifier,
    noVoiceNotice: NoVoiceNotice? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KosmoTheme.colors.background)
            .keepScreenOn(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val bars = SystemBarsPolicy.decide(isLandscape = maxWidth > maxHeight, isCricketGame = true)
            SystemBarsEffect(bars)
            val insets = when (bars.padding) {
                InsetsPadding.SAFE_DRAWING -> WindowInsets.safeDrawing
                InsetsPadding.CUTOUT_ONLY -> WindowInsets.displayCutout
            }
            BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(insets)) {
                if (bars.isHidden) {
                    LandscapeGame(game, onEvent, maxWidth.value, maxHeight.value, horizontalDp(insets))
                } else {
                    PortraitGame(game, onEvent, maxWidth.value)
                }
            }
        }
        if (game.isAbandonDialogShown) AbandonDialog(onEvent)
        noVoiceNotice?.let { NoVoiceDialog(it, onEvent) }
    }
}

/** The left plus right [insets], in dp: in landscape, the display cutout on the side of the camera hole. */
@Composable
private fun horizontalDp(insets: WindowInsets): Float {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    return (insets.getLeft(density, direction) + insets.getRight(density, direction)) / density.density
}

@Composable
private fun PortraitGame(game: CricketGameScreenState, onEvent: (AppEvent) -> Unit, screenWidthDp: Float) {
    Column(
        verticalArrangement = Arrangement.spacedBy(CricketBoardLayoutPolicy.GAP.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag(CricketTags.SCREEN)
            .padding(
                start = Spacing.screenHorizontal,
                end = Spacing.screenHorizontal,
                top = CricketBoardLayoutPolicy.TOP_MARGIN.dp,
                bottom = CricketBoardLayoutPolicy.BOTTOM_MARGIN.dp,
            ),
    ) {
        Header(game, Modifier.fillMaxWidth())
        // The zone and the board share the height the pinned parts leave; the policy splits it (R2.1): the rows keep
        // 48 to 56 dp and the zone takes all the rest, so nothing is left empty under the board.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val players = game.game.config.players.size
            val boardWidth = screenWidthDp - 2 * CricketBoardLayoutPolicy.SIDE_MARGIN
            val playerWidth = CricketBoardLayoutPolicy.columnWidths(boardWidth, players).first
            val header = rememberHeaderMetrics(
                game.headerTotals,
                playerWidth,
                CricketBoardLayoutPolicy.PORTRAIT_TOTAL_SIZES_SP,
            )
            val split = CricketBoardLayoutPolicy.portraitSplit(
                screenWidthDp,
                maxHeight.value - CricketBoardLayoutPolicy.GAP,
                header.heightDp,
                rememberLineHeightDp(KosmoTheme.typography.labelLarge),
                players,
                game.game.config.targets.size,
            )
            Column(verticalArrangement = Arrangement.spacedBy(CricketBoardLayoutPolicy.GAP.dp)) {
                Zone(game, Modifier.height(split.zoneHeightDp.dp).fillMaxWidth(), showsTotal = split.showsTotal)
                Board(game, split.board, header, onEvent)
            }
        }
        Actions(game, onEvent)
    }
}

/** "CRICKET · STANDARD" (CR22), then "SANS 25-BULL" when the Bull is not in play, and the M14 position. */
@Composable
internal fun Header(game: CricketGameScreenState, modifier: Modifier) {
    Box(modifier) {
        MatchHeader(
            mode = headerMode(game),
            position = positionText(game.headerPosition),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** "CRICKET · STANDARD", with "SANS 25-BULL" when the Bull is not in play (CR22). */
@Composable
internal fun headerMode(game: CricketGameScreenState): String {
    val config = game.game.config
    val resources = LocalResources.current
    val variant = when (config.variant) {
        CricketVariant.STANDARD -> R.string.cricket_variant_standard
        CricketVariant.CUT_THROAT -> R.string.cricket_variant_cut_throat
    }
    val parts = listOfNotNull(
        resources.getString(R.string.cricket_title),
        resources.getString(variant),
        resources.getString(R.string.cricket_header_without_bull).takeIf { !config.isBullInPlay },
    )
    return parts.joinToString(resources.getString(R.string.format_separator))
}

/** The zone of the highlighted player: the one whose turn waits, the winner of a pending win, else the thrower. */
@Composable
internal fun Zone(
    game: CricketGameScreenState,
    modifier: Modifier,
    isSingleLine: Boolean = false,
    showsTotal: Boolean = false,
) {
    val index = game.highlightedPlayerIndex ?: 0
    val locale = LocalConfiguration.current.locales[0]
    val mpr = formatMpr(game.mprOf(index), locale, stringResource(R.string.game_average_none))
    CricketPlayerZone(
        content = CricketZoneContent(
            index = index,
            name = game.game.config.players[index],
            slot = game.colorSlots[index],
            stat = ZoneStat(stringResource(R.string.cricket_zone_mpr, mpr)),
            indicator = game.indicators?.get(index),
            isVictory = game.zoneMode == CricketZoneMode.VICTORY,
            turnDarts = game.zoneDarts,
            nextDartIndex = game.nextDartIndex,
            total = game.zoneTotal,
            mprValue = mpr,
        ),
        modifier = modifier,
        isSingleLine = isSingleLine,
        showsTotal = showsTotal,
    )
}

@Composable
internal fun Board(
    game: CricketGameScreenState,
    layout: CricketBoardLayout,
    header: CricketHeaderMetrics,
    onEvent: (AppEvent) -> Unit,
    modifier: Modifier = Modifier,
    actions: CricketBoardActions? = null,
) {
    CricketBoard(
        content = CricketBoardContent(
            rows = game.boardRows,
            names = game.game.config.players,
            slots = game.colorSlots,
            currentIndex = game.highlightedPlayerIndex ?: 0,
            visits = game.visits,
            totals = game.headerTotals,
            buttons = game.buttonRows,
            isMissEnabled = game.isKeypadEnabled,
        ),
        layout = layout,
        header = header,
        onDart = { onEvent(AppEvent.CricketDartTapped(it)) },
        onMiss = { onEvent(AppEvent.MissTapped) },
        modifier = modifier,
        actions = actions,
    )
}

/**
 * "Undo" and "Next player"; while a win waits, "Undo" and the confirm button that names what it decides (M15, CR25):
 * "Confirm leg", "Confirm set" or "Confirm match". "Undo" removes the winning dart.
 */
@Composable
internal fun Actions(game: CricketGameScreenState, onEvent: (AppEvent) -> Unit, modifier: Modifier = Modifier) {
    val (undo, primary) = actionsOf(game, onEvent)
    Column(modifier = modifier.fillMaxWidth()) {
        ArchiveFailedMessage(game.archive)
        ActionBar(undo, primary, minHeight = States.minTouchTarget, modifier = Modifier.fillMaxWidth())
    }
}

/** The two actions, the same in both orientations: "Undo", then "Next player" or the confirm button (CR25). */
@Composable
internal fun actionsOf(game: CricketGameScreenState, onEvent: (AppEvent) -> Unit): Pair<BarAction, BarAction> {
    val undo = BarAction(stringResource(R.string.key_undo), KeypadTags.UNDO, game.canUndo) {
        onEvent(AppEvent.UndoTapped)
    }
    val outcome = game.winOutcome
    val primary = if (outcome == null) {
        BarAction(stringResource(R.string.key_next_player), KeypadTags.NEXT_PLAYER, game.canGoToNextPlayer) {
            onEvent(AppEvent.NextPlayerTapped)
        }
    } else {
        confirmAction(outcome, game.archive, onEvent)
    }
    return undo to primary
}
