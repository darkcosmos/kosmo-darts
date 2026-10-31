package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private const val TEXT_SCALE_REFERENCE = 24f

/**
 * Cricket game screen (docs/rules/cricket.md, CR22 to CR25). Portrait, top to bottom: header, compact zone, the marks
 * board, the keypad and the action bar. Landscape: header and board on the left; zone, keypad and action bar on the
 * right. The sizes come from [CricketLayoutPolicy]: the keypad and the action bar are pinned and never scroll; only
 * the board (and, in landscape, the zone) may scroll. The screen stays on, and system back opens "Abandon the game?"
 * (M15).
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
            val density = LocalDensity.current
            // The effective scale of a 24 sp text: Android 14 enlarges large text less than the setting says.
            val textScale = with(density) { TEXT_SCALE_REFERENCE.sp.toDp().value } / TEXT_SCALE_REFERENCE
            val labelSize = CricketLayoutPolicy.ROW_LABEL_TEXT_SIZE
            val labelScale = with(density) { labelSize.sp.toDp().value } / labelSize
            val config = game.game.config
            val players = config.players.size
            val layout = remember(maxWidth, maxHeight, textScale, labelScale, players, config.targets.size) {
                CricketLayoutPolicy.of(
                    CricketSpace(
                        maxWidth.value, maxHeight.value, textScale, players, config.targets.size, players > 1,
                        labelScale,
                    ),
                )
            }
            if (maxWidth > maxHeight) LandscapeGame(game, layout, onEvent) else PortraitGame(game, layout, onEvent)
        }
        if (game.isAbandonDialogShown) AbandonDialog(onEvent)
        noVoiceNotice?.let { NoVoiceDialog(it, onEvent) }
    }
}

@Composable
private fun PortraitGame(game: CricketGameScreenState, layout: CricketLayout, onEvent: (AppEvent) -> Unit) {
    val gap = layout.gapDp.dp
    Column(
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier
            .fillMaxSize()
            .testTag(CricketTags.SCREEN)
            .padding(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, top = layout.topMarginDp.dp),
    ) {
        Header(game, Modifier.height(layout.headerHeightDp.dp))
        Zone(game, Modifier.height(layout.zoneHeightDp.dp).fillMaxWidth())
        Board(game, layout, Modifier.weight(1f).fillMaxWidth())
        CricketKeypad(game, onEvent, keyHeight = layout.keyHeightDp.dp, gap = gap, Modifier.fillMaxWidth())
        Actions(game, layout, onEvent, Modifier.padding(bottom = layout.bottomMarginDp.dp))
    }
}

@Composable
private fun LandscapeGame(game: CricketGameScreenState, layout: CricketLayout, onEvent: (AppEvent) -> Unit) {
    val gap = layout.gapDp.dp
    Row(
        horizontalArrangement = Arrangement.spacedBy(CricketLayoutPolicy.COLUMN_GAP.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag(CricketTags.SCREEN)
            .padding(horizontal = Spacing.screenHorizontal),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier
                .width(layout.boardWidthDp.dp)
                .fillMaxHeight()
                .padding(top = layout.topMarginDp.dp, bottom = layout.bottomMarginDp.dp),
        ) {
            Header(game, Modifier.height(layout.headerHeightDp.dp))
            Board(game, layout, Modifier.weight(1f).fillMaxWidth())
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier.weight(1f).fillMaxHeight().padding(top = layout.topMarginDp.dp),
        ) {
            // The zone gives way first and then scrolls by itself: the keypad and the action bar never do.
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Zone(game, Modifier.height(layout.zoneHeightDp.dp).fillMaxWidth())
            }
            CricketKeypad(game, onEvent, keyHeight = layout.keyHeightDp.dp, gap = gap, Modifier.fillMaxWidth())
            Actions(game, layout, onEvent, Modifier.padding(bottom = layout.bottomMarginDp.dp))
        }
    }
}

/** "CRICKET · STANDARD" (CR22), then "SANS 25-BULL" when the Bull is not in play, and the M14 position. */
@Composable
private fun Header(game: CricketGameScreenState, modifier: Modifier) {
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
            position = positionText(game.headerPosition),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The zone of the highlighted player: the one whose turn waits, the winner of a pending win, else the thrower. */
@Composable
private fun Zone(game: CricketGameScreenState, modifier: Modifier) {
    val index = game.highlightedPlayerIndex ?: 0
    val locale = LocalConfiguration.current.locales[0]
    val mpr = formatMpr(game.mprOf(index), locale, stringResource(R.string.game_average_none))
    val score = when (val value = game.zoneScoreOf(index)) {
        is CricketZoneScore.Points -> value.points.toString()
        is CricketZoneScore.ClosedTargets -> stringResource(R.string.cricket_closed_targets, value.closed, value.total)
    }
    CricketPlayerZone(
        content = CricketZoneContent(
            index = index,
            name = game.game.config.players[index],
            slot = game.colorSlots[index],
            stat = ZoneStat(stringResource(R.string.cricket_zone_mpr, mpr)),
            indicator = game.indicators?.get(index),
            isVictory = game.zoneMode == CricketZoneMode.VICTORY,
            score = score,
            turnDarts = game.zoneDarts,
            nextDartIndex = game.nextDartIndex,
        ),
        modifier = modifier,
    )
}

@Composable
private fun Board(game: CricketGameScreenState, layout: CricketLayout, modifier: Modifier) {
    CricketBoard(
        content = CricketBoardContent(
            rows = game.boardRows,
            names = game.game.config.players,
            slots = game.colorSlots,
            currentIndex = game.highlightedPlayerIndex ?: 0,
            visits = game.visits,
            points = game.boardPoints,
        ),
        layout = layout,
        modifier = modifier,
    )
}

/**
 * "Undo" and "Next player"; while a win waits, "Undo" and the confirm button that names what it decides (M15, CR25):
 * "Confirm leg", "Confirm set" or "Confirm match". "Undo" removes the winning dart.
 */
@Composable
private fun Actions(
    game: CricketGameScreenState,
    layout: CricketLayout,
    onEvent: (AppEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
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
    ActionBar(undo, primary, minHeight = layout.actionBarMinHeightDp.dp, modifier = modifier.fillMaxWidth())
}
