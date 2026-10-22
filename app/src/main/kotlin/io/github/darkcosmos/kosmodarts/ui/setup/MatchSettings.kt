package io.github.darkcosmos.kosmodarts.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent

private val MIN_TOUCH_TARGET = 48.dp

/** M13: "Legs per set" (or "Legs" with one set) and "Sets", each as "First to N" with − and +. */
@Composable
internal fun MatchSettings(setup: SetupState, onEvent: (AppEvent) -> Unit) {
    SectionLabel(stringResource(R.string.setup_match_label))
    Stepper(
        StepperSpec(
            label = stringResource(if (setup.hasSets) R.string.setup_legs_per_set else R.string.setup_legs),
            value = setup.legsPerSet,
            valueDescription = if (setup.hasSets) {
                stringResource(R.string.setup_legs_per_set_value, setup.legsPerSet)
            } else {
                stringResource(R.string.setup_legs_value, setup.legsPerSet)
            },
            tagPrefix = SetupTags.LEGS,
        ),
        decrease = StepperButton(stringResource(R.string.setup_fewer_legs), setup.canDecreaseLegs) {
            onEvent(AppEvent.LegsPerSetChanged(-1))
        },
        increase = StepperButton(stringResource(R.string.setup_more_legs), setup.canIncreaseLegs) {
            onEvent(AppEvent.LegsPerSetChanged(1))
        },
    )
    Stepper(
        StepperSpec(
            label = stringResource(R.string.setup_sets),
            value = setup.sets,
            valueDescription = stringResource(R.string.setup_sets_value, setup.sets),
            tagPrefix = SetupTags.SETS,
        ),
        decrease = StepperButton(stringResource(R.string.setup_fewer_sets), setup.canDecreaseSets) {
            onEvent(AppEvent.SetsChanged(-1))
        },
        increase = StepperButton(stringResource(R.string.setup_more_sets), setup.canIncreaseSets) {
            onEvent(AppEvent.SetsChanged(1))
        },
    )
}

/** What a [Stepper] shows: its visible label, its value, the TalkBack text of the value and its test tags. */
private class StepperSpec(val label: String, val value: Int, val valueDescription: String, val tagPrefix: String)

/** A − or + button of a [Stepper]: its TalkBack text, whether it is enabled, and its action. */
private class StepperButton(val description: String, val isEnabled: Boolean, val onClick: () -> Unit)

/**
 * The label above the controls `− · First to N · +`, so that a long label at 200 % never squeezes the buttons (U4).
 * The value is read as "label: first to N" (U5); the visible label is hidden from TalkBack, so it is not read twice.
 */
@Composable
private fun Stepper(spec: StepperSpec, decrease: StepperButton, increase: StepperButton) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = spec.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .testTag(SetupTags.label(spec.tagPrefix))
                .semantics { hideFromAccessibility() },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val symbolRemove = stringResource(R.string.setup_remove_symbol)
            StepperIconButton(decrease, symbolRemove, SetupTags.decrease(spec.tagPrefix))
            Text(
                text = stringResource(R.string.setup_first_to, spec.value),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .testTag(SetupTags.value(spec.tagPrefix))
                    .semantics { contentDescription = spec.valueDescription },
            )
            StepperIconButton(increase, stringResource(R.string.setup_add_symbol), SetupTags.increase(spec.tagPrefix))
        }
    }
}

@Composable
private fun StepperIconButton(button: StepperButton, symbol: String, tag: String) {
    FilledTonalIconButton(
        onClick = button.onClick,
        enabled = button.isEnabled,
        modifier = Modifier
            .sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
            .testTag(tag)
            .semantics { contentDescription = button.description },
    ) {
        SymbolText(symbol)
    }
}
