package io.github.darkcosmos.kosmodarts.ui.headtohead

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.stats.CricketFilter
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.HeadToHeadEvent
import io.github.darkcosmos.kosmodarts.ui.profile.FilterGroup
import io.github.darkcosmos.kosmodarts.ui.profile.OUT_RULES
import io.github.darkcosmos.kosmodarts.ui.profile.PeriodGroup
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileTags
import io.github.darkcosmos.kosmodarts.ui.profile.START_SCORES
import io.github.darkcosmos.kosmodarts.ui.profile.VARIANTS
import io.github.darkcosmos.kosmodarts.ui.profile.outRuleLabel
import io.github.darkcosmos.kosmodarts.ui.profile.startScoreLabel
import io.github.darkcosmos.kosmodarts.ui.profile.variantLabel

/** HH6, ST3, ST4: the mode, the filters of the chosen mode (none for All modes), then the period. */
@Composable
internal fun HeadToHeadFilters(
    selections: HeadToHeadSelections,
    onEvent: (AppEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        FilterGroup(
            title = stringResource(R.string.heatmap_filter_mode),
            tag = HeadToHeadTags.MODE_FILTER,
            options = HeadToHeadMode.entries,
            selected = selections.mode,
            label = { mode -> modeLabel(mode) },
            onSelect = { onEvent(HeadToHeadEvent.ModeSelected(it)) },
        )
        when (selections.mode) {
            HeadToHeadMode.ALL -> Unit
            HeadToHeadMode.X01 -> X01Filters(selections, onEvent)
            HeadToHeadMode.CRICKET -> FilterGroup(
                title = stringResource(R.string.profile_filter_variant),
                tag = ProfileTags.VARIANT_FILTER,
                options = VARIANTS,
                selected = selections.cricketFilter.variant,
                label = { variant -> variantLabel(variant) },
                onSelect = { onEvent(HeadToHeadEvent.CricketFilterSelected(CricketFilter(it))) },
            )
        }
        PeriodGroup(selections.period) { onEvent(HeadToHeadEvent.PeriodSelected(it)) }
    }
}

@Composable
private fun X01Filters(selections: HeadToHeadSelections, onEvent: (AppEvent) -> Unit) {
    val filter = selections.x01Filter
    FilterGroup(
        title = stringResource(R.string.profile_filter_start_score),
        tag = ProfileTags.START_SCORE_FILTER,
        options = START_SCORES,
        selected = filter.startScore,
        label = { score -> startScoreLabel(score) },
        onSelect = { onEvent(HeadToHeadEvent.X01FilterSelected(filter.copy(startScore = it))) },
    )
    FilterGroup(
        title = stringResource(R.string.profile_filter_out_rule),
        tag = ProfileTags.OUT_RULE_FILTER,
        options = OUT_RULES,
        selected = filter.checkoutRule,
        label = { rule -> outRuleLabel(rule) },
        onSelect = { onEvent(HeadToHeadEvent.X01FilterSelected(filter.copy(checkoutRule = it))) },
    )
}

@Composable
private fun modeLabel(mode: HeadToHeadMode): String = when (mode) {
    HeadToHeadMode.ALL -> stringResource(R.string.heatmap_mode_all)
    HeadToHeadMode.X01 -> stringResource(R.string.profile_tab_x01)
    HeadToHeadMode.CRICKET -> stringResource(R.string.heatmap_mode_cricket)
}
