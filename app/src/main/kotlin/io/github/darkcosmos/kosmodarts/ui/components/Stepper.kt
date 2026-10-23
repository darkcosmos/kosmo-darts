package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States

/** Drawn size of the − and + buttons (design system, `Stepper`); their touch area is 48 dp (plan D3). */
private val DRAWN_BUTTON = 44.dp
private val VALUE_MIN_WIDTH = 40.dp

/** A − or + button of a [Stepper]: its symbol, TalkBack text, whether it is enabled, its action and test tag. */
class StepperButton(
    val symbol: String,
    val description: String,
    val isEnabled: Boolean,
    val tag: String,
    val onClick: () -> Unit,
)

/**
 * A setting with − and + (design system, `Stepper`; M13): the label, then − (44 dp drawn on `surfaceHigher`, 48 dp
 * touch area), the value, +. The value is read as "label: first to N"; the visible label is hidden from TalkBack so it
 * is not read twice. A disabled button is drawn at 40 %.
 */
@Composable
fun Stepper(
    label: String,
    value: Int,
    valueDescription: String,
    decrease: StepperButton,
    increase: StepperButton,
    labelTag: String,
    valueTag: String,
    modifier: Modifier = Modifier,
) {
    val colors = KosmoTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(KosmoShapes.control)
            .background(colors.surface)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Text(
            text = label,
            style = KosmoTheme.typography.labelLarge.copy(letterSpacing = KosmoTheme.typography.body.letterSpacing),
            color = colors.onBackground,
            modifier = Modifier
                .weight(1f)
                .testTag(labelTag)
                .semantics { hideFromAccessibility() },
        )
        StepperIconButton(decrease)
        Text(
            text = value.toString(),
            style = KosmoTheme.typography.keypad,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = VALUE_MIN_WIDTH)
                .testTag(valueTag)
                .semantics { contentDescription = valueDescription },
        )
        StepperIconButton(increase)
    }
}

@Composable
private fun StepperIconButton(button: StepperButton) {
    val colors = KosmoTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(States.minTouchTarget)
            .clickable(enabled = button.isEnabled, role = Role.Button, onClick = button.onClick)
            .semantics { contentDescription = button.description }
            .testTag(button.tag),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(DRAWN_BUTTON)
                .alpha(if (button.isEnabled) 1f else States.DISABLED_ALPHA)
                .clip(KosmoShapes.control)
                .background(colors.surfaceHigher),
        ) {
            Text(
                text = button.symbol,
                style = KosmoTheme.typography.keypad,
                color = colors.onBackground,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}
