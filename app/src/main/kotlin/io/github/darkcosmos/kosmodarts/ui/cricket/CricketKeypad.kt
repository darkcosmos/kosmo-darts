package io.github.darkcosmos.kosmodarts.ui.cricket

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.game.Key
import io.github.darkcosmos.kosmodarts.ui.game.KeypadTags
import io.github.darkcosmos.kosmodarts.ui.game.ModifierKey
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States

private const val NUMBERS_PER_LINE = 3
private const val CRICKET_NUMBERS_FIRST = 20
private const val WIDE_KEY_WEIGHT_WITH_BULL = 2f
private const val WIDE_KEY_WEIGHT_WITHOUT_BULL = 4f

/**
 * The Cricket keypad (CR24, design system `CricketKeypad`): three rows of keys built from the X01 `Key`.
 * - 20, 19, 18 and Double;
 * - 17, 16, 15 and Triple;
 * - 25, Bull and Miss (Miss alone with 25-Bull off).
 *
 * Double and Triple are one-shot modifiers (single by default). 25 and Bull are disabled while a modifier is active and
 * absent with 25-Bull off (Q11 A). The whole keypad is drawn at 35 % while a turn waits, as in X01.
 *
 * @param keyHeight from the layout policy, never below 48 dp.
 */
@Composable
fun CricketKeypad(
    game: CricketGameScreenState,
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
    val row: @Composable (@Composable RowScope.() -> Unit) -> Unit = { content ->
        Row(
            modifier = Modifier.fillMaxWidth().height(keyHeight),
            horizontalArrangement = Arrangement.spacedBy(gap),
            content = content,
        )
    }
    Column(
        modifier = modifier.testTag(CricketTags.KEYPAD).alpha(if (locked) States.LOCKED_KEYPAD_ALPHA else 1f),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        val numbers = (CRICKET_NUMBERS_FIRST downTo CRICKET_NUMBERS_FIRST - 5).toList().chunked(NUMBERS_PER_LINE)
        val modifiers = listOf(Multiplier.DOUBLE to R.string.key_double, Multiplier.TRIPLE to R.string.key_triple)
        numbers.zip(modifiers).forEach { (line, modifierKey) ->
            row {
                line.forEach { number -> NumberKey(number, game.isKeypadEnabled, locked, tap) }
                val (multiplier, label) = modifierKey
                ModifierKey(
                    isActive = game.multiplier == multiplier,
                    isEnabled = game.isKeypadEnabled,
                    label = stringResource(label),
                    tag = if (multiplier == Multiplier.DOUBLE) KeypadTags.DOUBLE else KeypadTags.TRIPLE,
                    onClick = { tap(AppEvent.MultiplierTapped(multiplier)) },
                )
            }
        }
        row { BottomKeys(game, locked, tap) }
    }
}

@Composable
private fun RowScope.NumberKey(number: Int, isEnabled: Boolean, isLocked: Boolean, tap: (AppEvent) -> Unit) {
    Key(
        text = stringResource(R.string.dart_single, number),
        tag = KeypadTags.number(number),
        style = KosmoTheme.typography.keypad,
        enabled = isEnabled,
        isLocked = isLocked,
        onClick = { tap(AppEvent.NumberTapped(number)) },
    )
}

/** 25, Bull and Miss: 25 and Bull only with 25-Bull in play (CR24). */
@Composable
private fun RowScope.BottomKeys(game: CricketGameScreenState, locked: Boolean, tap: (AppEvent) -> Unit) {
    val typography = KosmoTheme.typography
    if (game.areBullKeysShown) {
        Key(
            text = stringResource(R.string.dart_outer_bull),
            tag = KeypadTags.OUTER_BULL,
            style = typography.keypad,
            enabled = game.areBullKeysEnabled,
            isLocked = locked,
            onClick = { tap(AppEvent.OuterBullTapped) },
        )
        Key(
            text = stringResource(R.string.dart_bull),
            tag = KeypadTags.BULL,
            style = typography.keypadLabel,
            enabled = game.areBullKeysEnabled,
            isLocked = locked,
            onClick = { tap(AppEvent.BullTapped) },
        )
    }
    Key(
        text = stringResource(R.string.dart_miss),
        tag = KeypadTags.MISS,
        style = typography.keypadLabel,
        enabled = game.isKeypadEnabled,
        isLocked = locked,
        isOutlined = true,
        weight = if (game.areBullKeysShown) WIDE_KEY_WEIGHT_WITH_BULL else WIDE_KEY_WEIGHT_WITHOUT_BULL,
        onClick = { tap(AppEvent.MissTapped) },
    )
}
