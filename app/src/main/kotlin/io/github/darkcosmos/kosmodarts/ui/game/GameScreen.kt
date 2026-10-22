package io.github.darkcosmos.kosmodarts.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01PlayerState
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.NoVoiceNotice
import io.github.darkcosmos.kosmodarts.ui.format.averageText
import io.github.darkcosmos.kosmodarts.ui.format.dartText
import io.github.darkcosmos.kosmodarts.ui.format.dartsText
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme

private const val DARTS_PER_TURN = 3
private val PORTRAIT_KEY_ROW_HEIGHT = 58.dp
private val SPACING = 12.dp

// TalkBack order inside a player row and inside the highlighted card (VT8); see also ScoreLine.kt.
private const val ROW_ORDER_NAME = 0f
private const val ROW_ORDER_AVERAGE = 1f
private const val ROW_ORDER_REMAINING = 2f
private const val ROW_ORDER_BOX = 3f
private const val CARD_ORDER_NAME = 0f
// M14: the indicator line is read after the name in the card, and after the row line in a row.
private const val ORDER_INDICATORS = 0.5f
private const val ROW_ORDER_INDICATORS = 1f
private const val CARD_ORDER_SUGGESTION = 4f
private const val CARD_ORDER_DARTS = 5f

/**
 * Game screen. Portrait: scores above the keypad. Landscape: keypad beside the scores. The screen stays on while it
 * is displayed, and system back opens the "Abandon the game?" confirmation.
 */
@Composable
fun GameScreen(
    game: GameScreenState,
    onEvent: (AppEvent) -> Unit,
    modifier: Modifier = Modifier,
    noVoiceNotice: NoVoiceNotice? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .keepScreenOn(),
        color = MaterialTheme.colorScheme.background,
    ) {
        BoxWithConstraints(modifier = Modifier.safeDrawingPadding().padding(SPACING)) {
            if (maxWidth > maxHeight) {
                Row(horizontalArrangement = Arrangement.spacedBy(SPACING)) {
                    Scoreboard(game, Modifier.weight(1f).fillMaxHeight())
                    Keypad(game, onEvent, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(SPACING)) {
                    Scoreboard(game, Modifier.weight(1f).fillMaxWidth())
                    Keypad(game, onEvent, Modifier.fillMaxWidth().height(PORTRAIT_KEY_ROW_HEIGHT * KEYPAD_ROWS))
                }
            }
        }
        if (game.isAbandonDialogShown) {
            AbandonDialog(onEvent)
        }
        game.pendingTurn?.takeIf { game.isCheckoutDialogShown }?.let { turn ->
            CheckoutDialog(
                game.game.state.players[turn.playerIndex].name,
                turn.darts,
                game.checkoutDialogOutcome,
                onEvent,
            )
        }
        noVoiceNotice?.let { NoVoiceDialog(it, onEvent) }
    }
}

/**
 * The players in configuration order: the highlighted card and the other rows. The highlighted card is scrolled
 * fully into view each time [GameScreenState.highlightedPlayerIndex] changes, and on first display (U1, M14).
 */
@Composable
private fun Scoreboard(game: GameScreenState, modifier: Modifier) {
    val state = game.game.state
    val visitBoxes = visitBoxTexts(game)
    val indicators = game.indicators
    val currentCard = remember { BringIntoViewRequester() }
    LaunchedEffect(game.highlightedPlayerIndex) {
        // After the frame that lays the card out at its new place.
        withFrameNanos {}
        currentCard.bringIntoView()
    }
    Column(
        modifier = modifier
            .testTag(GameTags.SCOREBOARD)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.players.forEachIndexed { index, player ->
            val figures = PlayerFigures(player, index, game.averageOf(index), indicators?.get(index))
            if (index == game.highlightedPlayerIndex) {
                CurrentPlayerCard(
                    figures,
                    game.highlightedTurnDarts,
                    visitBoxes[index],
                    game.isBustShown,
                    game.checkoutSuggestion,
                    Modifier.bringIntoViewRequester(currentCard),
                )
            } else {
                PlayerRow(figures, visitBoxes[index])
            }
        }
    }
}

/**
 * What a card or a row shows of a player besides the turn: the leg figures, the match average (M9) and the match
 * indicators (M14, `null` in a single game).
 */
private class PlayerFigures(
    val player: X01PlayerState,
    val index: Int,
    val average: Double?,
    val indicator: MatchIndicator?,
)

@Composable
private fun CurrentPlayerCard(
    figures: PlayerFigures,
    turnDarts: List<Dart>,
    visitBox: VisitBoxText?,
    isBust: Boolean,
    checkoutSuggestion: List<Dart>?,
    modifier: Modifier = Modifier,
) {
    val player = figures.player
    val index = figures.index
    val toThrow = stringResource(R.string.game_to_throw, player.name)
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .testTag(GameTags.CURRENT_PLAYER),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .semantics { isTraversalGroup = true },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics { traversalIndex = CARD_ORDER_NAME },
            ) {
                Text(
                    text = player.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(GameTags.playerName(index))
                        .semantics {
                            heading()
                            contentDescription = toThrow
                        },
                )
                PlayerAverage(figures.average, index, MaterialTheme.typography.titleMedium)
            }
            figures.indicator?.let {
                MatchIndicators(it, index, Modifier.semantics { traversalIndex = ORDER_INDICATORS })
            }
            CardScoreLine(player.remaining, isBust, visitBox, index, SPACING)
            CheckoutSuggestionLine(checkoutSuggestion, CARD_ORDER_SUGGESTION)
            DartSlots(turnDarts, Modifier.semantics { traversalIndex = CARD_ORDER_DARTS })
        }
    }
}

@Composable
private fun DartSlots(darts: List<Dart>, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        repeat(DARTS_PER_TURN) { index ->
            val dart = darts.getOrNull(index)
            val text = dart?.let { dartText(it) }.orEmpty()
            val description = if (dart == null) {
                stringResource(R.string.game_dart_slot_empty, index + 1)
            } else {
                stringResource(R.string.game_dart_slot, index + 1, text)
            }
            Surface(
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag(GameTags.dartSlot(index))
                    .clearAndSetSemantics { contentDescription = description },
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

/** A player who is not highlighted; in a match, the indicator line goes under the row (M14). */
@Composable
private fun PlayerRow(figures: PlayerFigures, visitBox: VisitBoxText?) {
    val indicator = figures.indicator
    if (indicator == null) {
        PlayerRowLine(figures, visitBox)
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { isTraversalGroup = true },
        ) {
            PlayerRowLine(figures, visitBox)
            MatchIndicators(
                indicator,
                figures.index,
                Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp)
                    .semantics { traversalIndex = ROW_ORDER_INDICATORS },
            )
        }
    }
}

@Composable
private fun PlayerRowLine(figures: PlayerFigures, visitBox: VisitBoxText?) {
    val player = figures.player
    val index = figures.index
    val nameStyle = MaterialTheme.typography.titleMedium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { isTraversalGroup = true },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SPACING),
    ) {
        // VT11: the name keeps at least NAME_MIN_REFERENCE; the average goes under it when the line is too narrow.
        OneOrTwoLines(
            minFirstWidth = measuredWidth(NAME_MIN_REFERENCE, nameStyle),
            secondLineAlignment = Alignment.Start,
            spacing = SPACING,
            modifier = Modifier.weight(1f),
            first = {
                Text(
                    text = player.name,
                    style = nameStyle,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(GameTags.playerName(index))
                        .semantics { traversalIndex = ROW_ORDER_NAME },
                )
            },
            trailing = {
                PlayerAverage(
                    figures.average,
                    index,
                    MaterialTheme.typography.bodyMedium,
                    Modifier.semantics { traversalIndex = ROW_ORDER_AVERAGE },
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        RowRemaining(player.remaining, index)
        // A row box is never hidden: only the card hides it, while its bust waits (VT13).
        visitBox?.let {
            VisitTotalBox(
                text = it,
                index = index,
                style = nameStyle,
                borderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { traversalIndex = ROW_ORDER_BOX },
            )
        }
    }
}

/** Remaining score of a player row. */
@Composable
private fun RowRemaining(remaining: Int, index: Int) {
    Text(
        text = remaining.toString(),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .testTag(GameTags.remaining(index))
            .semantics { traversalIndex = ROW_ORDER_REMAINING },
    )
}

/** Three-dart average of the player over the whole match (M9). */
@Composable
private fun PlayerAverage(
    average: Double?,
    index: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    Text(
        text = stringResource(R.string.game_average, averageText(average)),
        style = style,
        color = color,
        modifier = modifier.testTag(GameTags.average(index)),
    )
}

/**
 * Modal confirmation of the winning dart. It cannot be dismissed by tapping outside or with system back: the only
 * ways out are "Finish …" and Undo. The confirm button names what it decides (M15): "Finish leg", "Finish set", or
 * "Finish game" for the match and a single game.
 */
@Composable
private fun CheckoutDialog(
    playerName: String,
    darts: List<Dart>,
    outcome: LegOutcome?,
    onEvent: (AppEvent) -> Unit,
) {
    val finishLabel = when (outcome) {
        LegOutcome.LEG -> R.string.checkout_finish_leg
        LegOutcome.SET -> R.string.checkout_finish_set
        LegOutcome.MATCH, null -> R.string.checkout_finish
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        modifier = Modifier.testTag(GameTags.CHECKOUT_DIALOG),
        title = { Text(stringResource(R.string.checkout_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = playerName, style = MaterialTheme.typography.titleLarge)
                Text(text = dartsText(darts), style = MaterialTheme.typography.headlineSmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(AppEvent.FinishGameTapped) }) {
                Text(stringResource(finishLabel))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(AppEvent.UndoTapped) }) {
                Text(stringResource(R.string.key_undo))
            }
        },
    )
}

/** The "Abandon the game?" confirmation, shared by the game screen and the between-legs screen (M16, R6). */
@Composable
internal fun AbandonDialog(onEvent: (AppEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(AppEvent.AbandonDismissed) },
        title = { Text(stringResource(R.string.game_abandon_title)) },
        text = { Text(stringResource(R.string.game_abandon_text)) },
        confirmButton = {
            TextButton(onClick = { onEvent(AppEvent.AbandonConfirmed) }) {
                Text(stringResource(R.string.game_abandon_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(AppEvent.AbandonDismissed) }) {
                Text(stringResource(R.string.game_abandon_cancel))
            }
        },
    )
}

@Preview
@Composable
private fun GameScreenPreview() {
    val game = X01Game(X01Config(listOf("Ann", "Bob")), listOf(Dart.triple(20), Dart.single(5)))
    KosmoDartsTheme {
        GameScreen(game = GameScreenState(game), onEvent = {})
    }
}
