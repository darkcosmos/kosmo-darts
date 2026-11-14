package io.github.darkcosmos.kosmodarts.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.stats.CricketFilter
import io.github.darkcosmos.kosmodarts.engine.stats.Period
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.ProfileEvent
import io.github.darkcosmos.kosmodarts.ui.components.SectionLabel
import io.github.darkcosmos.kosmodarts.ui.components.SegmentedChoice
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States

private val START_SCORES: List<Int?> = listOf(null, 501, 301)
private val OUT_RULES: List<CheckoutRule?> = listOf(null, CheckoutRule.DOUBLE_OUT, CheckoutRule.SINGLE_OUT)
private val VARIANTS: List<CricketVariant?> = listOf(null, CricketVariant.STANDARD, CricketVariant.CUT_THROAT)

/** ST3, ST4: the filters of the shown section, then its period. Shown in every state of the section (PS6). */
@Composable
internal fun ProfileFilters(state: ProfileState, onEvent: (AppEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        when (state.section) {
            ProfileSection.X01 -> X01Filters(state, onEvent)
            ProfileSection.CRICKET -> CricketFilters(state, onEvent)
            ProfileSection.HEATMAP -> Unit
        }
    }
}

@Composable
private fun X01Filters(state: ProfileState, onEvent: (AppEvent) -> Unit) {
    FilterGroup(
        title = stringResource(R.string.profile_filter_start_score),
        tag = ProfileTags.START_SCORE_FILTER,
        options = START_SCORES,
        selected = state.x01Filter.startScore,
        label = { score -> startScoreLabel(score) },
        onSelect = { onEvent(ProfileEvent.X01FilterSelected(state.x01Filter.copy(startScore = it))) },
    )
    FilterGroup(
        title = stringResource(R.string.profile_filter_out_rule),
        tag = ProfileTags.OUT_RULE_FILTER,
        options = OUT_RULES,
        selected = state.x01Filter.checkoutRule,
        label = { rule -> outRuleLabel(rule) },
        onSelect = { onEvent(ProfileEvent.X01FilterSelected(state.x01Filter.copy(checkoutRule = it))) },
    )
    PeriodGroup(state.x01Period) { onEvent(ProfileEvent.X01PeriodSelected(it)) }
}

@Composable
private fun CricketFilters(state: ProfileState, onEvent: (AppEvent) -> Unit) {
    FilterGroup(
        title = stringResource(R.string.profile_filter_variant),
        tag = ProfileTags.VARIANT_FILTER,
        options = VARIANTS,
        selected = state.cricketFilter.variant,
        label = { variant -> variantLabel(variant) },
        onSelect = { onEvent(ProfileEvent.CricketFilterSelected(CricketFilter(it))) },
    )
    PeriodGroup(state.cricketPeriod) { onEvent(ProfileEvent.CricketPeriodSelected(it)) }
}

@Composable
private fun PeriodGroup(selected: Period, onSelect: (Period) -> Unit) {
    FilterGroup(
        title = stringResource(R.string.profile_filter_period),
        tag = ProfileTags.PERIOD_FILTER,
        options = Period.entries,
        selected = selected,
        label = { period -> periodLabel(period) },
        onSelect = onSelect,
    )
}

@Composable
private fun <T> FilterGroup(
    title: String,
    tag: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        if (LocalDensity.current.fontScale >= STACK_FONT_SCALE) {
            StackedChoice(options, selected, label, onSelect, Modifier.testTag(tag))
        } else {
            SegmentedChoice(
                options = options,
                selected = selected,
                label = label,
                onSelect = onSelect,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(tag),
            )
        }
    }
}

/**
 * The same choice as [SegmentedChoice], one option per line: at a large font three labels no longer fit side by side
 * and a word would be cut (U11).
 */
@Composable
private fun <T> StackedChoice(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier,
) {
    val colors = KosmoTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth().selectableGroup()) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = States.minTouchTarget)
                    .clip(KosmoShapes.control)
                    .background(if (isSelected) colors.accent else colors.surface)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Text(
                    text = label(option),
                    style = KosmoTheme.typography.button,
                    color = if (isSelected) colors.onAccent else colors.onBackground,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** From this font scale on, the options of a filter are stacked (as in the actions panel, U4, U6). */
private const val STACK_FONT_SCALE = 1.3f

@Composable
private fun startScoreLabel(score: Int?): String = if (score == null) {
    stringResource(R.string.history_filter_all)
} else {
    stringResource(R.string.profile_start_score, score)
}

@Composable
private fun outRuleLabel(rule: CheckoutRule?): String = when (rule) {
    null -> stringResource(R.string.history_filter_all)
    CheckoutRule.DOUBLE_OUT -> stringResource(R.string.setup_double_out)
    CheckoutRule.SINGLE_OUT -> stringResource(R.string.setup_single_out)
}

@Composable
private fun variantLabel(variant: CricketVariant?): String = when (variant) {
    null -> stringResource(R.string.history_filter_all)
    CricketVariant.STANDARD -> stringResource(R.string.cricket_variant_standard)
    CricketVariant.CUT_THROAT -> stringResource(R.string.cricket_variant_cut_throat)
}

@Composable
private fun periodLabel(period: Period): String = when (period) {
    Period.SINCE_ALWAYS -> stringResource(R.string.profile_period_always)
    Period.LAST_30_DAYS -> stringResource(R.string.profile_period_30_days)
    Period.LAST_10_MATCHES -> stringResource(R.string.profile_period_10_matches)
}
