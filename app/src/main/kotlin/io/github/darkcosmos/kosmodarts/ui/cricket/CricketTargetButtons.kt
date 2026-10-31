package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States

/** The drawn key is inset by this much on each side of its touch area, so adjacent keys show a 4 dp gap (CR24). */
internal val KEY_INSET = 2.dp
private val DOT_SIZE = 5.dp
private val DOT_GAP = 3.dp
private val DOTS_TOP_GAP = 2.dp

/**
 * The buttons of one target row (CR24, design system `CricketTargetButtons`): single, double, triple from the start to
 * the end (the 25 then the Bull in the Bull row), sharing [widthDp]. A tap records one dart. Buttons of a dead row stay
 * enabled and only take the muted label (CR15); a locked button is drawn at [States.LOCKED_KEYPAD_ALPHA].
 */
@Composable
internal fun CricketTargetButtons(
    buttons: List<CricketTargetButton>,
    isDead: Boolean,
    widthDp: Float,
    onDart: (Dart) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.width(widthDp.dp).fillMaxHeight()) {
        buttons.forEach { button ->
            TargetButton(button, isDead, Modifier.weight(1f).fillMaxHeight(), onDart)
        }
    }
}

@Composable
private fun TargetButton(button: CricketTargetButton, isDead: Boolean, modifier: Modifier, onDart: (Dart) -> Unit) {
    val dart = button.dart
    val color = if (isDead) KosmoTheme.colors.onBackgroundMuted else KosmoTheme.colors.onBackground
    BoardKey(
        description = dartDescription(dart),
        tags = KeyTags(CricketTags.dartButton(dart), CricketTags.dartKey(dart)),
        isEnabled = button.isEnabled,
        onClick = { onDart(dart) },
        modifier = modifier,
    ) {
        Text(
            text = if (dart.segment in Dart.NUMBERS) {
                stringResource(R.string.dart_single, dart.segment)
            } else {
                stringResource(R.string.dart_bull)
            },
            style = KosmoTheme.typography.keypadLabel,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
        Dots(button.dotCount, color, Modifier.testTag(CricketTags.dartDots(dart)))
    }
}

/** The TalkBack text of a button: « 20 », « Double 20 », « Triple 20 », « 25 », « Bull » (CR24). */
@Composable
private fun dartDescription(dart: Dart): String = when {
    dart.segment == Dart.OUTER_BULL_SEGMENT -> stringResource(R.string.announce_dart_outer_bull)
    dart.segment == Dart.BULL_SEGMENT -> stringResource(R.string.announce_dart_bull)
    dart.multiplier == Multiplier.DOUBLE -> stringResource(R.string.cricket_button_double, dart.segment.toString())
    dart.multiplier == Multiplier.TRIPLE -> stringResource(R.string.cricket_button_triple, dart.segment.toString())
    else -> stringResource(R.string.announce_dart_single, dart.segment)
}

/** [count] dots under a number; the row keeps its height at 0 dots so that all the numbers line up. */
@Composable
private fun Dots(count: Int, color: Color, modifier: Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(DOT_GAP),
        modifier = modifier
            .padding(top = DOTS_TOP_GAP)
            .height(DOT_SIZE)
            .semantics { dotCount = count },
    ) {
        repeat(count) { Box(Modifier.size(DOT_SIZE).clip(CircleShape).background(color)) }
    }
}

/** The test tags of a [BoardKey]: its whole touch area, and its drawn key. */
internal class KeyTags(val touchArea: String, val key: String)

/**
 * One key of the board's button column. The touch area is the whole cell (48 x 48 dp at least); the drawn key is inset
 * by [KEY_INSET] inside it. [isOutlined] is the transparent "Miss" key. A disabled key is drawn at
 * [States.LOCKED_KEYPAD_ALPHA]; an enabled one is at full opacity, dead or not.
 */
@Composable
internal fun BoardKey(
    description: String,
    tags: KeyTags,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isOutlined: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = KosmoTheme.colors
    val haptics = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag(tags.touchArea)
            .clickable(enabled = isEnabled, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                onClick()
            }
            .semantics { contentDescription = description },
    ) {
        val shape = KosmoShapes.control
        val outline = if (isOutlined) Modifier.border(BorderStroke(1.dp, colors.outline), shape) else Modifier
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(KEY_INSET)
                .alpha(if (isEnabled) 1f else States.LOCKED_KEYPAD_ALPHA)
                .clip(shape)
                .background(if (isOutlined) Color.Transparent else colors.surfaceHigh)
                .then(outline)
                .testTag(tags.key),
        ) {
            content()
        }
    }
}
