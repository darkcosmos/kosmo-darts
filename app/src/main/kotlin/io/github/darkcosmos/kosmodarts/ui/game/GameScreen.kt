package io.github.darkcosmos.kosmodarts.ui.game

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01PlayerState
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.format.averageText
import io.github.darkcosmos.kosmodarts.ui.format.dartText
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme

private const val DARTS_PER_TURN = 3
private val PORTRAIT_KEY_ROW_HEIGHT = 58.dp
private val SPACING = 12.dp
private val REMAINING_SIZE = 88.sp

/**
 * Game screen. Portrait: scores above the keypad. Landscape: keypad beside the scores. The screen stays on while it
 * is displayed, and system back opens the "Abandon the game?" confirmation.
 */
@Composable
fun GameScreen(game: GameScreenState, onEvent: (AppEvent) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .keepScreenOn(),
        color = MaterialTheme.colorScheme.background,
    ) {
        BoxWithConstraints(modifier = Modifier.safeDrawingPadding().padding(SPACING)) {
            if (maxWidth > maxHeight) {
                Row(horizontalArrangement = Arrangement.spacedBy(SPACING)) {
                    Scoreboard(game, onEvent, Modifier.weight(1f).fillMaxHeight())
                    Keypad(game, onEvent, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(SPACING)) {
                    Scoreboard(game, onEvent, Modifier.weight(1f).fillMaxWidth())
                    Keypad(game, onEvent, Modifier.fillMaxWidth().height(PORTRAIT_KEY_ROW_HEIGHT * KEYPAD_ROWS))
                }
            }
        }
        if (game.isAbandonDialogShown) {
            AbandonDialog(onEvent)
        }
    }
}

@Composable
private fun Scoreboard(game: GameScreenState, onEvent: (AppEvent) -> Unit, modifier: Modifier) {
    val state = game.game.state
    val lastTurns = lastTurnScores(state)
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        game.pendingTurn?.let { turn ->
            TurnPanel(turn, state.players[turn.playerIndex].name, onEvent)
        }
        state.players.forEachIndexed { index, player ->
            if (index == game.highlightedPlayerIndex) {
                CurrentPlayerCard(player, index, game.highlightedTurnDarts, lastTurns[index])
            } else {
                PlayerRow(player, index, lastTurns[index])
            }
        }
    }
}

@Composable
private fun CurrentPlayerCard(player: X01PlayerState, index: Int, turnDarts: List<Dart>, lastTurn: LastTurnScore) {
    val toThrow = stringResource(R.string.game_to_throw, player.name)
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(GameTags.CURRENT_PLAYER),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = player.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            heading()
                            contentDescription = toThrow
                        },
                )
                PlayerStats(player, index, lastTurn, MaterialTheme.typography.titleMedium)
            }
            Text(
                text = player.remaining.toString(),
                fontSize = REMAINING_SIZE,
                lineHeight = REMAINING_SIZE,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag(GameTags.CURRENT_REMAINING),
            )
            DartSlots(turnDarts)
        }
    }
}

@Composable
private fun DartSlots(darts: List<Dart>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun PlayerRow(player: X01PlayerState, index: Int, lastTurn: LastTurnScore) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = player.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        PlayerStats(
            player,
            index,
            lastTurn,
            MaterialTheme.typography.bodyMedium,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = player.remaining.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** Three-dart average, and the score of the last completed turn under it. */
@Composable
private fun PlayerStats(
    player: X01PlayerState,
    index: Int,
    lastTurn: LastTurnScore,
    style: TextStyle,
    color: Color = Color.Unspecified,
) {
    Column(horizontalAlignment = Alignment.End) {
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
