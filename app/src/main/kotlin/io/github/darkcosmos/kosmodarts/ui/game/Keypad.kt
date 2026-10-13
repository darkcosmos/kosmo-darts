package io.github.darkcosmos.kosmodarts.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent

private const val NUMBERS_PER_ROW = 5
private val KEY_SPACING = 6.dp
private val MIN_KEY_HEIGHT = 48.dp
private val MIN_LABEL_SIZE = 8.sp
private val MAX_LABEL_SIZE = 22.sp

/** Rows of the keypad: four rows of numbers, the modifiers and specials, then undo. */
const val KEYPAD_ROWS: Int = 6

/** Number grid 1 to 20 in numeric order, Double/Triple one-shot modifiers, 25, Bull, Miss and Undo. */
@Composable
fun Keypad(game: GameScreenState, onEvent: (AppEvent) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val tap: (AppEvent) -> Unit = { event ->
        haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        onEvent(event)
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(KEY_SPACING)) {
        Dart.NUMBERS.chunked(NUMBERS_PER_ROW).forEach { numbers ->
            KeyRow {
                numbers.forEach { number ->
                    Key(
                        text = stringResource(R.string.dart_single, number),
                        tag = KeypadTags.number(number),
                        enabled = game.isKeypadEnabled,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        onClick = { tap(AppEvent.NumberTapped(number)) },
                    )
                }
            }
        }
        KeyRow {
            ModifierKey(game, Multiplier.DOUBLE, stringResource(R.string.key_double), KeypadTags.DOUBLE, tap)
            ModifierKey(game, Multiplier.TRIPLE, stringResource(R.string.key_triple), KeypadTags.TRIPLE, tap)
            Key(
                text = stringResource(R.string.dart_outer_bull),
                tag = KeypadTags.OUTER_BULL,
                enabled = game.areBullKeysEnabled,
                onClick = { tap(AppEvent.OuterBullTapped) },
            )
            Key(
                text = stringResource(R.string.dart_bull),
                tag = KeypadTags.BULL,
                enabled = game.areBullKeysEnabled,
                onClick = { tap(AppEvent.BullTapped) },
            )
            Key(
                text = stringResource(R.string.dart_miss),
                tag = KeypadTags.MISS,
                enabled = game.isKeypadEnabled,
                onClick = { tap(AppEvent.MissTapped) },
            )
        }
        KeyRow {
            Key(
                text = stringResource(R.string.key_undo),
                tag = KeypadTags.UNDO,
                enabled = game.canUndo,
                colors = ButtonDefaults.outlinedButtonColors(),
                onClick = { tap(AppEvent.UndoTapped) },
            )
        }
    }
}

@Composable
private fun ColumnScope.KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KEY_SPACING),
        content = content,
    )
}

/** Highlighted while active; exposed as selected to accessibility services. */
@Composable
private fun RowScope.ModifierKey(
    game: GameScreenState,
    multiplier: Multiplier,
    text: String,
    tag: String,
    tap: (AppEvent) -> Unit,
) {
    val isActive = game.multiplier == multiplier
    Key(
        text = text,
        tag = tag,
        enabled = game.isKeypadEnabled,
        colors = if (isActive) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
        modifier = Modifier.semantics { selected = isActive },
        onClick = { tap(AppEvent.MultiplierTapped(multiplier)) },
    )
}

@Composable
private fun RowScope.Key(
    text: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
) {
    val isOutlined = colors == ButtonDefaults.outlinedButtonColors()
    val borderColor = if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        border = if (isOutlined) BorderStroke(1.dp, borderColor) else null,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(4.dp),
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .heightIn(min = MIN_KEY_HEIGHT)
            .testTag(tag),
    ) {
        Text(
            text = text,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = MAX_LABEL_SIZE),
        )
    }
}
