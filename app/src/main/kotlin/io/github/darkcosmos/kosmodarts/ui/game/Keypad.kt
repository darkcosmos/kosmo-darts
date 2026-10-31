package io.github.darkcosmos.kosmodarts.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States

private const val NUMBERS_PER_ROW = 5

/** Rows of the keypad: four rows of numbers, then Double, Triple, 25, Bull and Miss (design system, `Keypad`). */
const val KEYPAD_ROWS: Int = 5

/**
 * Number grid 1 to 20 in numeric order, then the Double and Triple one-shot modifiers, 25, Bull and Miss (design
 * system, `Keypad`). The whole keypad is drawn at 35 % while it is locked (a turn waits: BUST, CHECKOUT or "Next
 * player"). Double and Triple: inactive like the other keys; active on `accent` with a 2 dp inner border and a check
 * mark (owner X1, U8), and "selected" for TalkBack; at most one is active.
 *
 * @param keyHeight from the layout policy, never below 48 dp.
 */
@Composable
fun Keypad(
    game: GameScreenState,
    onEvent: (AppEvent) -> Unit,
    keyHeight: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val tap: (AppEvent) -> Unit = { event ->
        haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        onEvent(event)
    }
    val locked = !game.isKeypadEnabled
    Column(
        modifier = modifier.alpha(if (locked) States.LOCKED_KEYPAD_ALPHA else 1f),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        val row: @Composable (@Composable RowScope.() -> Unit) -> Unit = { content ->
            Row(
                modifier = Modifier.fillMaxWidth().height(keyHeight),
                horizontalArrangement = Arrangement.spacedBy(gap),
                content = content,
            )
        }
        val numberStyle = KosmoTheme.typography.keypad
        Dart.NUMBERS.chunked(NUMBERS_PER_ROW).forEach { numbers ->
            row {
                numbers.forEach { number ->
                    Key(
                        text = stringResource(R.string.dart_single, number),
                        tag = KeypadTags.number(number),
                        style = numberStyle,
                        enabled = game.isKeypadEnabled,
                        isLocked = locked,
                        onClick = { tap(AppEvent.NumberTapped(number)) },
                    )
                }
            }
        }
        row { SpecialKeys(game, tap, locked) }
    }
}

/** Double, Triple, 25, Bull and Miss: the last row of the keypad. */
@Composable
private fun RowScope.SpecialKeys(game: GameScreenState, tap: (AppEvent) -> Unit, locked: Boolean) {
    val numberStyle = KosmoTheme.typography.keypad
    listOf(
        Triple(Multiplier.DOUBLE, R.string.key_double, KeypadTags.DOUBLE),
        Triple(Multiplier.TRIPLE, R.string.key_triple, KeypadTags.TRIPLE),
    ).forEach { (multiplier, label, tag) ->
        ModifierKey(
            isActive = game.multiplier == multiplier,
            isEnabled = game.isKeypadEnabled,
            label = stringResource(label),
            tag = tag,
            onClick = { tap(AppEvent.MultiplierTapped(multiplier)) },
        )
    }
    Key(
        text = stringResource(R.string.dart_outer_bull),
        tag = KeypadTags.OUTER_BULL,
        style = numberStyle,
        enabled = game.areBullKeysEnabled,
        isLocked = locked,
        onClick = { tap(AppEvent.OuterBullTapped) },
    )
    Key(
        text = stringResource(R.string.dart_bull),
        tag = KeypadTags.BULL,
        style = KosmoTheme.typography.keypadLabel,
        enabled = game.areBullKeysEnabled,
        isLocked = locked,
        onClick = { tap(AppEvent.BullTapped) },
    )
    Key(
        text = stringResource(R.string.dart_miss),
        tag = KeypadTags.MISS,
        style = KosmoTheme.typography.keypadLabel,
        enabled = game.isKeypadEnabled,
        isLocked = locked,
        isOutlined = true,
        onClick = { tap(AppEvent.MissTapped) },
    )
}
