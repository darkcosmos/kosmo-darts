package io.github.darkcosmos.kosmodarts.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import io.github.darkcosmos.kosmodarts.R
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
private val REMAINING_SIZE = 88.sp
private val LINE_GAP = 2.dp
private val BOX_PADDING = 8.dp

// Absorbs the rounding of the measured text width to whole pixels, so the box text never needs a second line.
private val BOX_SLACK = 1.dp

// VT11: a box fits BUST and this number, the highest visit total.
private const val BOX_NUMBER_REFERENCE = "180"

// VT11: a player name always gets at least the width of these six characters in its style.
internal const val NAME_MIN_REFERENCE: String = "Mmmmmm"

// VT11: the remaining score is laid out for the widest three-digit score, so the layout never switches as it drops.
private const val REMAINING_REFERENCE = "888"

// TalkBack order inside a player row and inside the highlighted card (VT8).
private const val ROW_ORDER_NAME = 0f
private const val ROW_ORDER_STATS = 1f
private const val ROW_ORDER_REMAINING = 2f
private const val ROW_ORDER_BOX = 3f
private const val CARD_ORDER_NAME = 0f
private const val CARD_ORDER_REMAINING = 1f
private const val CARD_ORDER_BUST = 2f
private const val CARD_ORDER_BOX = 3f
private const val CARD_ORDER_DARTS = 4f

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
            CheckoutDialog(game.game.state.players[turn.playerIndex].name, turn.darts, onEvent)
        }
        noVoiceNotice?.let { NoVoiceDialog(it, onEvent) }
    }
}

@Composable
private fun Scoreboard(game: GameScreenState, modifier: Modifier) {
    val state = game.game.state
    val lastTurns = lastTurnScores(state)
    val visitBoxes = visitBoxTexts(game)
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.players.forEachIndexed { index, player ->
            if (index == game.highlightedPlayerIndex) {
                CurrentPlayerCard(
                    player,
                    index,
                    game.highlightedTurnDarts,
                    lastTurns[index],
                    visitBoxes[index],
                    game.isBustShown,
                )
            } else {
                PlayerRow(player, index, lastTurns[index], visitBoxes[index])
            }
        }
    }
}

@Composable
private fun CurrentPlayerCard(
    player: X01PlayerState,
    index: Int,
    turnDarts: List<Dart>,
    lastTurn: LastTurnScore,
    visitBox: VisitBoxText,
    isBust: Boolean,
) {
    val toThrow = stringResource(R.string.game_to_throw, player.name)
    val remainingStyle = LocalTextStyle.current.merge(
        TextStyle(fontSize = REMAINING_SIZE, lineHeight = REMAINING_SIZE, fontWeight = FontWeight.Bold),
    )
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
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
                PlayerStats(player, index, lastTurn, MaterialTheme.typography.titleMedium)
            }
            // VT11: the remaining score keeps the whole first line; the BUST badge and the box go under it if needed.
            OneOrTwoLines(
                minFirstWidth = measuredWidth(REMAINING_REFERENCE, remainingStyle),
                secondLineAlignment = Alignment.End,
                first = {
                    Text(
                        text = player.remaining.toString(),
                        style = remainingStyle,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .testTag(GameTags.CURRENT_REMAINING)
                            .semantics { traversalIndex = CARD_ORDER_REMAINING },
                    )
                },
                trailing = {
                    if (isBust) {
                        BustBadge(Modifier.semantics { traversalIndex = CARD_ORDER_BUST })
                    }
                    VisitTotalBox(
                        text = visitBox,
                        index = index,
                        style = MaterialTheme.typography.titleLarge,
                        borderColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.semantics { traversalIndex = CARD_ORDER_BOX },
                    )
                },
            )
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

@Composable
private fun PlayerRow(player: X01PlayerState, index: Int, lastTurn: LastTurnScore, visitBox: VisitBoxText) {
    val nameStyle = MaterialTheme.typography.titleMedium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { isTraversalGroup = true },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SPACING),
    ) {
        // VT11: the name keeps at least NAME_MIN_REFERENCE; the stats go under it when the line is too narrow.
        OneOrTwoLines(
            minFirstWidth = measuredWidth(NAME_MIN_REFERENCE, nameStyle),
            secondLineAlignment = Alignment.Start,
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
                PlayerStats(
                    player,
                    index,
                    lastTurn,
                    MaterialTheme.typography.bodyMedium,
                    Modifier.semantics { traversalIndex = ROW_ORDER_STATS },
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        Text(
            text = player.remaining.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .testTag(GameTags.remaining(index))
                .semantics { traversalIndex = ROW_ORDER_REMAINING },
        )
        VisitTotalBox(
            text = visitBox,
            index = index,
            style = nameStyle,
            borderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { traversalIndex = ROW_ORDER_BOX },
        )
    }
}

/**
 * Framed visit total of a player (VT8 to VT11). Its width fits the widest of BUST and [BOX_NUMBER_REFERENCE] in
 * [style], so it never changes with the value shown.
 */
@Composable
private fun VisitTotalBox(
    text: VisitBoxText,
    index: Int,
    style: TextStyle,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    val args = text.formatArgs.toTypedArray()
    val value = stringResource(text.valueRes, *args)
    val description = stringResource(text.descriptionRes, *args)
    val bust = stringResource(R.string.game_bust)
    val textWidth = maxOf(measuredWidth(bust, style), measuredWidth(BOX_NUMBER_REFERENCE, style))
    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        border = BorderStroke(1.dp, borderColor),
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .width(textWidth + BOX_PADDING * 2 + BOX_SLACK)
            .testTag(GameTags.visitTotal(index))
            // One TalkBack node: the description is read instead of the bare number, which tests can still read.
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Text(
            text = value,
            style = style,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = BOX_PADDING, vertical = BOX_PADDING / 2),
        )
    }
}

/** Width of [text] laid out on one line in [style], at the current density and font scale. Render-only. */
@Composable
private fun measuredWidth(text: String, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, style, density, measurer) {
        with(density) { measurer.measure(text, style, softWrap = false, maxLines = 1).size.width.toDp() }
    }
}

/**
 * Places [trailing] on the same line as [first] when [first] still gets at least [minFirstWidth]; otherwise [first]
 * takes the whole line and [trailing] goes on a second line, aligned with [secondLineAlignment] (VT11). Layout only:
 * no game logic, nothing stored.
 */
@Composable
private fun OneOrTwoLines(
    minFirstWidth: Dp,
    secondLineAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
    first: @Composable () -> Unit,
    trailing: @Composable RowScope.() -> Unit,
) {
    Layout(
        contents = listOf(
            { Box { first() } },
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SPACING),
                    content = trailing,
                )
            },
        ),
        modifier = modifier,
    ) { (firstMeasurables, trailingMeasurables), constraints ->
        val maxWidth = constraints.maxWidth
        val spacing = SPACING.roundToPx()
        val trailingPlaceable = trailingMeasurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val firstWidthOnOneLine = maxWidth - spacing - trailingPlaceable.width
        val isOneLine = firstWidthOnOneLine >= minFirstWidth.roundToPx()
        val firstWidth = if (isOneLine) firstWidthOnOneLine else maxWidth
        val firstPlaceable = firstMeasurables.single().measure(
            Constraints(minWidth = firstWidth, maxWidth = firstWidth, maxHeight = constraints.maxHeight),
        )
        if (isOneLine) {
            val height = maxOf(firstPlaceable.height, trailingPlaceable.height)
            layout(maxWidth, height) {
                firstPlaceable.placeRelative(0, (height - firstPlaceable.height) / 2)
                trailingPlaceable.placeRelative(firstWidth + spacing, (height - trailingPlaceable.height) / 2)
            }
        } else {
            val secondLineTop = firstPlaceable.height + LINE_GAP.roundToPx()
            layout(maxWidth, secondLineTop + trailingPlaceable.height) {
                firstPlaceable.placeRelative(0, 0)
                val x = secondLineAlignment.align(trailingPlaceable.width, maxWidth, layoutDirection)
                trailingPlaceable.place(x, secondLineTop)
            }
        }
    }
}

/** Three-dart average, and the score of the last completed turn under it. */
@Composable
private fun PlayerStats(
    player: X01PlayerState,
    index: Int,
    lastTurn: LastTurnScore,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        Text(
            text = stringResource(R.string.game_average, averageText(player.threeDartAverage)),
            style = style,
            color = color,
        )
        Text(
            text = stringResource(R.string.game_last_turn_score, lastTurnText(lastTurn)),
            style = style,
            color = color,
            modifier = Modifier.testTag(GameTags.lastTurnScore(index)),
        )
    }
}

@Composable
private fun lastTurnText(lastTurn: LastTurnScore): String = when (lastTurn) {
    LastTurnScore.None -> stringResource(R.string.game_last_turn_none)
    LastTurnScore.Bust -> stringResource(R.string.game_bust)
    is LastTurnScore.Points -> lastTurn.points.toString()
}

/** Shown in the card of the player whose bust turn waits for "Next player". */
@Composable
private fun BustBadge(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .testTag(GameTags.CARD_BUST)
            .semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Text(
            text = stringResource(R.string.game_bust),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

/**
 * Modal confirmation of the winning dart. It cannot be dismissed by tapping outside or with system back: the only
 * ways out are "Finish game" and Undo.
 */
@Composable
private fun CheckoutDialog(playerName: String, darts: List<Dart>, onEvent: (AppEvent) -> Unit) {
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
                Text(stringResource(R.string.checkout_finish))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(AppEvent.UndoTapped) }) {
                Text(stringResource(R.string.key_undo))
            }
        },
    )
}

@Composable
private fun AbandonDialog(onEvent: (AppEvent) -> Unit) {
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
