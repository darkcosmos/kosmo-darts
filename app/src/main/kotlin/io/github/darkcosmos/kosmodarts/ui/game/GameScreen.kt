package io.github.darkcosmos.kosmodarts.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.NoVoiceNotice
import io.github.darkcosmos.kosmodarts.ui.components.ActionBar
import io.github.darkcosmos.kosmodarts.ui.components.BarAction
import io.github.darkcosmos.kosmodarts.ui.components.CurrentPlayerZone
import io.github.darkcosmos.kosmodarts.ui.components.DialogAction
import io.github.darkcosmos.kosmodarts.ui.components.KosmoDialog
import io.github.darkcosmos.kosmodarts.ui.components.MatchHeader
import io.github.darkcosmos.kosmodarts.ui.components.PlayerTile
import io.github.darkcosmos.kosmodarts.ui.components.TileContent
import io.github.darkcosmos.kosmodarts.ui.components.ZoneContent
import io.github.darkcosmos.kosmodarts.ui.format.modeText
import io.github.darkcosmos.kosmodarts.ui.format.positionText
import io.github.darkcosmos.kosmodarts.ui.layout.GameLayout
import io.github.darkcosmos.kosmodarts.ui.layout.GameLayoutPolicy
import io.github.darkcosmos.kosmodarts.ui.layout.GameSpace
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.Spacing

private val COLUMN_GAP = 12.dp
private const val TEXT_SCALE_REFERENCE = 24f

/**
 * Game screen (design system 6.1 to 6.3). Portrait, top to bottom: header, the current player's zone (all the free
 * height), the other players' tiles, the keypad and the action bar. Landscape: header, zone and tiles on the left,
 * keypad and action bar on the right. The sizes come from [GameLayoutPolicy] for the space left by the system bars,
 * the cutout and the keyboard. The screen stays on, and system back opens "Abandon the game?" in every zone mode
 * (M15, owner O1).
 */
@Composable
fun GameScreen(
    game: GameScreenState,
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
            val players = game.game.config.players.size
            val isMatch = !game.match.format.isSingleGame
            val isCheckout = game.zoneMode == ZoneMode.CHECKOUT
            val layout = remember(maxWidth, maxHeight, textScale, players, isMatch, isCheckout) {
                GameLayoutPolicy.of(GameSpace(maxWidth.value, maxHeight.value, textScale, players, isMatch, isCheckout))
            }
            val isLandscape = maxWidth > maxHeight
            if (isLandscape) LandscapeGame(game, layout, onEvent) else PortraitGame(game, layout, onEvent)
        }
        if (game.isAbandonDialogShown) AbandonDialog(onEvent)
        noVoiceNotice?.let { NoVoiceDialog(it, onEvent) }
    }
}

@Composable
private fun PortraitGame(game: GameScreenState, layout: GameLayout, onEvent: (AppEvent) -> Unit) {
    val gap = layout.gapDp.dp
    val margin = layout.verticalMarginDp.dp
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(GameTags.SCREEN)
            .padding(horizontal = Spacing.screenHorizontal),
    ) {
        FillColumn(
            gap = gap,
            topPadding = margin,
            bottomPadding = gap,
            fillMinHeight = layout.zoneMinHeightDp.dp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            before = listOf { Header(game) },
            fill = { Zone(game, layout, isLandscape = false) },
            after = listOf(
                { Tiles(game, layout) },
                {
                    Keypad(game, onEvent, keyHeight = layout.keyHeightDp.dp, gap = gap, Modifier.fillMaxWidth())
                },
            ),
        )
        // U10: pinned under the scrolling part, so "Undo" and the main action are always on screen.
        GameActions(game, layout, onEvent, Modifier.padding(bottom = margin))
    }
}

@Composable
private fun LandscapeGame(game: GameScreenState, layout: GameLayout, onEvent: (AppEvent) -> Unit) {
    val gap = layout.gapDp.dp
    val margin = layout.verticalMarginDp.dp
    Row(
        horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
        modifier = Modifier
            .fillMaxSize()
            .testTag(GameTags.SCREEN)
            .padding(horizontal = Spacing.screenHorizontal),
    ) {
        FillColumn(
            gap = gap,
            topPadding = margin,
            bottomPadding = margin,
            fillMinHeight = layout.zoneMinHeightDp.dp,
            modifier = Modifier
                .width(layout.leftColumnWidthDp.dp)
                .fillMaxHeight(),
            before = listOf { Header(game) },
            fill = { Zone(game, layout, isLandscape = true) },
            after = listOf { Tiles(game, layout) },
        )
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // The space under the keypad takes the rest; the action bar is pinned under it (U10).
            FillColumn(
                gap = gap,
                topPadding = margin,
                bottomPadding = 0.dp,
                fillMinHeight = 0.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                before = listOf {
                    Keypad(game, onEvent, keyHeight = layout.keyHeightDp.dp, gap = gap, Modifier.fillMaxWidth())
                },
                fill = { Spacer(Modifier) },
                after = emptyList(),
            )
            GameActions(game, layout, onEvent, Modifier.padding(bottom = margin))
        }
    }
}

@Composable
private fun Header(game: GameScreenState) {
    val config = game.game.config
    MatchHeader(
        mode = modeText(config.startScore, config.checkoutRule),
        position = positionText(game.headerPosition),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The zone of the highlighted player (the one whose turn waits, else the player to throw). */
@Composable
private fun Zone(game: GameScreenState, layout: GameLayout, isLandscape: Boolean) {
    val index = game.highlightedPlayerIndex ?: 0
    val player = game.game.state.players[index]
    val content = ZoneContent(
        index = index,
        name = player.name,
        slot = game.colorSlotOf(index),
        average = game.averageOf(index),
        indicator = game.indicators?.get(index),
        mode = game.zoneMode,
        remaining = player.remaining,
        route = game.checkoutSuggestion,
        turnDarts = game.highlightedTurnDarts,
        nextDartIndex = game.nextDartIndex,
        visitBox = visitBoxTexts(game)[index],
        checkout = game.checkoutSummary,
    )
    CurrentPlayerZone(
        content = content,
        scoreSize = layout.scoreSizeDp,
        isLandscape = isLandscape,
        modifier = Modifier.fillMaxSize(),
    )
}

/** M6: the other players in play order from the next one, [GameLayout.tilesPerRow] per line. */
@Composable
private fun Tiles(game: GameScreenState, layout: GameLayout) {
    val order = game.tileOrder
    if (order.isEmpty()) return
    val boxes = visitBoxTexts(game)
    val state = game.game.state
    Column(verticalArrangement = Arrangement.spacedBy(layout.gapDp.dp)) {
        order.chunked(layout.tilesPerRow).forEach { line ->
            // Tiles of a line share the height of the tallest one (a two-line name, a box under the score).
            Row(
                horizontalArrangement = Arrangement.spacedBy(layout.gapDp.dp),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                line.forEach { index ->
                    val player = state.players[index]
                    PlayerTile(
                        content = TileContent(
                            index = index,
                            name = player.name,
                            slot = game.colorSlotOf(index),
                            remaining = player.remaining,
                            visitBox = boxes[index],
                            indicator = game.indicators?.get(index),
                            isNext = index == order.first(),
                        ),
                        variant = layout.tileVariant,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                // A short last line keeps the tile width of the full lines.
                repeat(layout.tilesPerRow - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * "Undo" and "Next player"; while a checkout waits, "Undo" and the confirm button that names what it decides (M15,
 * owner Q5): "Confirm leg", "Confirm set", or "Confirm match" for the match and a single game. "Undo" stays enabled
 * during a BUST (owner Q3).
 */
@Composable
private fun GameActions(
    game: GameScreenState,
    layout: GameLayout,
    onEvent: (AppEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val undo = BarAction(stringResource(R.string.key_undo), KeypadTags.UNDO, game.canUndo) {
        onEvent(AppEvent.UndoTapped)
    }
    val outcome = game.checkoutOutcome
    val primary = if (outcome == null) {
        BarAction(stringResource(R.string.key_next_player), KeypadTags.NEXT_PLAYER, game.canGoToNextPlayer) {
            onEvent(AppEvent.NextPlayerTapped)
        }
    } else {
        BarAction(confirmLabel(outcome), GameTags.CONFIRM, isEnabled = true) { onEvent(AppEvent.FinishGameTapped) }
    }
    ActionBar(undo, primary, minHeight = layout.actionBarMinHeightDp.dp, modifier = modifier.fillMaxWidth())
}

@Composable
private fun confirmLabel(outcome: LegOutcome): String = when (outcome) {
    LegOutcome.LEG -> stringResource(R.string.checkout_confirm_leg)
    LegOutcome.SET -> stringResource(R.string.checkout_confirm_set)
    LegOutcome.MATCH -> stringResource(R.string.checkout_confirm_match)
}

/** The "Abandon the game?" confirmation, shared by the game screen and the between-legs screen (M16, R6). */
@Composable
internal fun AbandonDialog(onEvent: (AppEvent) -> Unit) {
    KosmoDialog(
        onDismissRequest = { onEvent(AppEvent.AbandonDismissed) },
        title = stringResource(R.string.game_abandon_title),
        text = stringResource(R.string.game_abandon_text),
        confirm = DialogAction(stringResource(R.string.game_abandon_confirm)) { onEvent(AppEvent.AbandonConfirmed) },
        dismiss = DialogAction(stringResource(R.string.game_abandon_cancel)) { onEvent(AppEvent.AbandonDismissed) },
    )
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun GameScreenPreview() {
    val game = X01Game(X01Config(listOf("Ann", "Bob", "Cid")), listOf(Dart.triple(20), Dart.single(5)))
    KosmoDartsTheme {
        GameScreen(game = GameScreenState(game), onEvent = {})
    }
}
