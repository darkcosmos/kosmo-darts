package io.github.darkcosmos.kosmodarts.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States

private const val NUMBERS_PER_ROW = 5
private val ACTIVE_BORDER = 2.dp

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
    ModifierKey(game, Multiplier.DOUBLE, stringResource(R.string.key_double), KeypadTags.DOUBLE, tap)
    ModifierKey(game, Multiplier.TRIPLE, stringResource(R.string.key_triple), KeypadTags.TRIPLE, tap)
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

/** Double or Triple (owner X1, U8): the active one shows a check mark and an inner border, and is "selected". */
@Composable
private fun RowScope.ModifierKey(
    game: GameScreenState,
    multiplier: Multiplier,
    label: String,
    tag: String,
    tap: (AppEvent) -> Unit,
) {
    val isActive = game.multiplier == multiplier
    val mark = stringResource(R.string.key_selected_mark)
    Key(
        text = if (isActive) "$mark $label" else label,
        tag = tag,
        style = KosmoTheme.typography.keypadLabel,
        enabled = game.isKeypadEnabled,
        isLocked = !game.isKeypadEnabled,
        isActive = isActive,
        modifier = Modifier.semantics {
            selected = isActive
            // TalkBack reads the label, not the check mark: the selected state already says it.
            contentDescription = label
        },
        onClick = { tap(AppEvent.MultiplierTapped(multiplier)) },
    )
}

@Composable
private fun RowScope.Key(
    text: String,
    tag: String,
    style: TextStyle,
    enabled: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isOutlined: Boolean = false,
) {
    val colors = KosmoTheme.colors
    val container = when {
        isActive -> colors.accent
        isOutlined -> Color.Transparent
        else -> colors.surfaceHigh
    }
    val content = when {
        isActive -> colors.onAccent
        isOutlined -> colors.onBackgroundMuted
        else -> colors.onBackground
    }
    // A key disabled inside an unlocked keypad (bull keys with a modifier) is at 40 %; a locked keypad is dimmed as a
    // whole, so its keys keep their colors under the keypad alpha.
    val disabledAlpha = if (isLocked) 1f else States.DISABLED_ALPHA
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = KosmoShapes.control,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = container.alpha * disabledAlpha),
            disabledContentColor = content.copy(alpha = disabledAlpha),
        ),
        border = when {
            isActive -> BorderStroke(ACTIVE_BORDER, colors.onBackground)
            isOutlined -> BorderStroke(1.dp, colors.outline)
            else -> null
        },
        contentPadding = PaddingValues(2.dp),
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .testTag(tag),
    ) {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(maxFontSize = style.fontSize),
        )
    }
}
