package io.github.darkcosmos.kosmodarts.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.components.SectionLabel
import io.github.darkcosmos.kosmodarts.ui.components.Stepper
import io.github.darkcosmos.kosmodarts.ui.components.StepperButton

/**
 * M13 (as amended by issue #49, D5): "MATCH", then "Sets · first to" and "Legs · first to", in this order and with
 * these labels in every format, each with − and +.
 */
@Composable
internal fun MatchSettings(setup: SetupState, onEvent: (AppEvent) -> Unit) {
    val minus = stringResource(R.string.setup_minus_symbol)
    val plus = stringResource(R.string.setup_add_symbol)
    SectionLabel(stringResource(R.string.setup_match_label))
    Stepper(
        label = stringResource(R.string.setup_sets_first_to),
        value = setup.sets,
        valueDescription = stringResource(R.string.setup_sets_value, setup.sets),
        decrease = StepperButton(
            minus,
            stringResource(R.string.setup_fewer_sets),
            setup.canDecreaseSets,
            SetupTags.decrease(SetupTags.SETS),
        ) { onEvent(AppEvent.SetsChanged(-1)) },
        increase = StepperButton(
            plus,
            stringResource(R.string.setup_more_sets),
            setup.canIncreaseSets,
            SetupTags.increase(SetupTags.SETS),
        ) { onEvent(AppEvent.SetsChanged(1)) },
        labelTag = SetupTags.label(SetupTags.SETS),
        valueTag = SetupTags.value(SetupTags.SETS),
    )
    Stepper(
        label = stringResource(R.string.setup_legs_first_to),
        value = setup.legsPerSet,
        valueDescription = stringResource(R.string.setup_legs_value, setup.legsPerSet),
        decrease = StepperButton(
            minus,
            stringResource(R.string.setup_fewer_legs),
            setup.canDecreaseLegs,
            SetupTags.decrease(SetupTags.LEGS),
        ) { onEvent(AppEvent.LegsPerSetChanged(-1)) },
        increase = StepperButton(
            plus,
            stringResource(R.string.setup_more_legs),
            setup.canIncreaseLegs,
            SetupTags.increase(SetupTags.LEGS),
        ) { onEvent(AppEvent.LegsPerSetChanged(1)) },
        labelTag = SetupTags.label(SetupTags.LEGS),
        valueTag = SetupTags.value(SetupTags.LEGS),
    )
}
