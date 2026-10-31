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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalConfiguration
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
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.game.KeypadTags
import io.github.darkcosmos.kosmodarts.ui.game.NoVoiceDialog
import io.github.darkcosmos.kosmodarts.ui.game.confirmLabel
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.Spacing
import io.github.darkcosmos.kosmodarts.ui.theme.States

/**
 * Cricket game screen (docs/rules/cricket.md, CR22 to CR25). Portrait, top to bottom: header, compact zone, the board
 * with its buttons, the action bar. Landscape ([LandscapeGame]): the board on the left; title, zone and action bar on
 * the right. The sizes come from [CricketBoardLayoutPolicy], from values the screen measures with Compose. The screen
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
        BoxWithConstraints(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            if (maxWidth > maxHeight) {
                LandscapeGame(game, onEvent, maxWidth.value, maxHeight.value)
            } else {
                PortraitGame(game, onEvent, maxWidth.value)
            }
        }
        if (game.isAbandonDialogShown) AbandonDialog(onEvent)
        noVoiceNotice?.let { NoVoiceDialog(it, onEvent) }
    }
}

@Composable
private fun PortraitGame(game: CricketGameScreenState, onEvent: (AppEvent) -> Unit, screenWidthDp: Float) {
    val zoneHeight = CricketBoardLayoutPolicy.portraitZoneHeightDp(rememberLineHeightDp(KosmoTheme.typography.label))
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
        Zone(game, Modifier.height(zoneHeight.dp).fillMaxWidth())
        // The board takes the height the pinned parts leave; the policy decides the rows (48 to 56 dp) from it.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val players = game.game.config.players.size
            val boardWidth = screenWidthDp - 2 * CricketBoardLayoutPolicy.SIDE_MARGIN
            val playerWidth = CricketBoardLayoutPolicy.columnWidths(boardWidth, players).first
            val header = rememberHeaderMetrics(
                game.headerTotals,
                playerWidth,
                CricketBoardLayoutPolicy.PORTRAIT_TOTAL_SIZES_SP,
            )
            val layout = CricketBoardLayoutPolicy.portrait(
                screenWidthDp,
                maxHeight.value,
                header.heightDp,
                players,
                game.game.config.targets.size,
            )
            Board(game, layout, header, onEvent)
        }
        Actions(game, onEvent)
    }
}

/** "CRICKET · STANDARD" (CR22), then "SANS 25-BULL" when the Bull is not in play, and the M14 position. */
@Composable
internal fun Header(game: CricketGameScreenState, modifier: Modifier, isShortPosition: Boolean = false) {
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
    Box(modifier) {
        MatchHeader(
            mode = parts.joinToString(resources.getString(R.string.format_separator)),
            position = positionText(game.headerPosition, isShortPosition),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The zone of the highlighted player: the one whose turn waits, the winner of a pending win, else the thrower. */
@Composable
internal fun Zone(game: CricketGameScreenState, modifier: Modifier, isSingleLine: Boolean = false) {
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
        ),
        modifier = modifier,
        isSingleLine = isSingleLine,
    )
}

@Composable
internal fun Board(
    game: CricketGameScreenState,
    layout: CricketBoardLayout,
    header: CricketHeaderMetrics,
    onEvent: (AppEvent) -> Unit,
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
    )
}

/**
 * "Undo" and "Next player"; while a win waits, "Undo" and the confirm button that names what it decides (M15, CR25):
 * "Confirm leg", "Confirm set" or "Confirm match". "Undo" removes the winning dart.
 */
@Composable
internal fun Actions(game: CricketGameScreenState, onEvent: (AppEvent) -> Unit, modifier: Modifier = Modifier) {
    val undo = BarAction(stringResource(R.string.key_undo), KeypadTags.UNDO, game.canUndo) {
        onEvent(AppEvent.UndoTapped)
    }
    val outcome = game.winOutcome
    val primary = if (outcome == null) {
        BarAction(stringResource(R.string.key_next_player), KeypadTags.NEXT_PLAYER, game.canGoToNextPlayer) {
            onEvent(AppEvent.NextPlayerTapped)
        }
    } else {
        BarAction(confirmLabel(outcome), GameTags.CONFIRM, isEnabled = true) { onEvent(AppEvent.FinishGameTapped) }
    }
    ActionBar(undo, primary, minHeight = States.minTouchTarget, modifier = modifier.fillMaxWidth())
}
