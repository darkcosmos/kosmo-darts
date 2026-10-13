package io.github.darkcosmos.kosmodarts.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.TurnOutcome
import io.github.darkcosmos.kosmodarts.engine.x01.X01Turn
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.format.dartsText

private val MIN_TOUCH_TARGET = 48.dp

/**
 * Confirmation of a completed turn: player, darts, total (or BUST) and remaining score, then Undo and "Next player"
 * ("Confirm" after a checkout).
 */
@Composable
fun TurnPanel(turn: X01Turn, playerName: String, onEvent: (AppEvent) -> Unit, modifier: Modifier = Modifier) {
    val isBust = turn.outcome == TurnOutcome.BUST
    val isCheckout = turn.outcome == TurnOutcome.CHECKOUT
    Surface(
        color = if (isBust) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (isBust) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .testTag(GameTags.TURN_PANEL)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = playerName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isCheckout) {
                    Text(
                        text = stringResource(R.string.panel_checkout),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dartsText(turn.darts),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (isBust) {
                        stringResource(R.string.game_bust)
                    } else {
                        stringResource(R.string.panel_total, turn.remainingBefore - turn.remainingAfter)
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                )
            }
            Text(
                text = stringResource(R.string.panel_remaining, turn.remainingAfter),
                style = MaterialTheme.typography.titleMedium,
            )
            PanelActions(isCheckout, onEvent)
        }
    }
}

@Composable
private fun PanelActions(isCheckout: Boolean, onEvent: (AppEvent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val buttonModifier = Modifier
            .weight(1f)
            .sizeIn(minHeight = MIN_TOUCH_TARGET)
        OutlinedButton(
            onClick = { onEvent(AppEvent.UndoTapped) },
            border = BorderStroke(1.dp, LocalContentColor.current),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalContentColor.current),
            modifier = buttonModifier,
        ) {
            Text(stringResource(R.string.key_undo))
        }
        Button(onClick = { onEvent(AppEvent.TurnConfirmed) }, modifier = buttonModifier) {
            Text(stringResource(if (isCheckout) R.string.panel_confirm else R.string.panel_next_player))
        }
    }
}
