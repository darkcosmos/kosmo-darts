package io.github.darkcosmos.kosmodarts.ui.profile

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone
import io.github.darkcosmos.kosmodarts.engine.stats.CricketFilter
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.ProfileEvent
import io.github.darkcosmos.kosmodarts.ui.board.Dartboard
import io.github.darkcosmos.kosmodarts.ui.board.HeatmapLegend
import io.github.darkcosmos.kosmodarts.ui.board.HeatmapScale
import io.github.darkcosmos.kosmodarts.ui.board.ZoneBand
import io.github.darkcosmos.kosmodarts.ui.format.formatPercent
import io.github.darkcosmos.kosmodarts.ui.components.SectionLabel
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import java.util.Locale

/** HM3, ST3, ST4: the mode chips, the filters of the chosen mode (none for All modes), then the period. */
@Composable
internal fun HeatmapFilters(selections: HeatmapSelections, onEvent: (AppEvent) -> Unit) {
    FilterGroup(
        title = stringResource(R.string.heatmap_filter_mode),
        tag = HeatmapTags.MODE_FILTER,
        options = HeatmapMode.entries,
        selected = selections.mode,
        label = { mode -> modeLabel(mode) },
        onSelect = { onEvent(ProfileEvent.HeatmapModeSelected(it)) },
    )
    when (selections.mode) {
        HeatmapMode.ALL -> Unit
        HeatmapMode.X01 -> HeatmapX01Filters(selections, onEvent)
        HeatmapMode.CRICKET -> FilterGroup(
            title = stringResource(R.string.profile_filter_variant),
            tag = ProfileTags.VARIANT_FILTER,
            options = VARIANTS,
            selected = selections.cricketFilter.variant,
            label = { variant -> variantLabel(variant) },
            onSelect = { onEvent(ProfileEvent.HeatmapCricketFilterSelected(CricketFilter(it))) },
        )
    }
    PeriodGroup(selections.period) { onEvent(ProfileEvent.HeatmapPeriodSelected(it)) }
}

@Composable
private fun HeatmapX01Filters(selections: HeatmapSelections, onEvent: (AppEvent) -> Unit) {
    val filter = selections.x01Filter
    FilterGroup(
        title = stringResource(R.string.profile_filter_start_score),
        tag = ProfileTags.START_SCORE_FILTER,
        options = START_SCORES,
        selected = filter.startScore,
        label = { score -> startScoreLabel(score) },
        onSelect = { onEvent(ProfileEvent.HeatmapX01FilterSelected(filter.copy(startScore = it))) },
    )
    FilterGroup(
        title = stringResource(R.string.profile_filter_out_rule),
        tag = ProfileTags.OUT_RULE_FILTER,
        options = OUT_RULES,
        selected = filter.checkoutRule,
        label = { rule -> outRuleLabel(rule) },
        onSelect = { onEvent(ProfileEvent.HeatmapX01FilterSelected(filter.copy(checkoutRule = it))) },
    )
}

@Composable
private fun modeLabel(mode: HeatmapMode): String = when (mode) {
    HeatmapMode.ALL -> stringResource(R.string.heatmap_mode_all)
    HeatmapMode.X01 -> stringResource(R.string.profile_tab_x01)
    HeatmapMode.CRICKET -> stringResource(R.string.heatmap_mode_cricket)
}

/**
 * The Heatmap section (HM2 to HM8, PS8): the board coloured by the relative scale, the band of the tapped zone, the
 * misses line with its HM4 label, the legend and the top 5 list, which is the reliable access to the figures; or the
 * explicit empty state when the selection has no dart (HM7). Every text comes from [state]; nothing is counted here.
 */
@Composable
internal fun HeatmapSection(state: HeatmapSectionState, onEvent: (AppEvent) -> Unit, modifier: Modifier = Modifier) {
    if (state.isEmpty) {
        Text(
            text = stringResource(R.string.heatmap_empty),
            style = KosmoTheme.typography.body,
            color = KosmoTheme.colors.onBackgroundMuted,
            modifier = modifier
                .fillMaxWidth()
                .testTag(HeatmapTags.EMPTY),
        )
        return
    }
    val resources = profileResources()
    val locale = profileLocale()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.fillMaxWidth()) {
        HeatmapBoard(state, { onEvent(ProfileEvent.HeatmapZoneTapped(it)) }, resources, locale)
        HeatmapBand(state, resources, locale)
        HeatmapMisses(state, resources, locale)
        HeatmapLegend(maxShare = HeatmapScale.maxShare(state.zoneShares))
        TopZones(state, resources, locale)
    }
}

/** The board coloured by the relative scale (HM5, HM8); a tap on a zone goes to [onZoneTapped]. */
@Composable
internal fun HeatmapBoard(
    state: HeatmapSectionState,
    onZoneTapped: (BoardZone) -> Unit,
    resources: Resources,
    locale: Locale,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val colors = remember(state.zoneShares) { HeatmapScale.colors(state.zoneShares) }
    val zoneDescription = remember(state, resources, locale) {
        { zone: BoardZone ->
            state.figureOf(zone).description(resources, locale)
        }
    }
    Dartboard(
        colors = colors,
        neutral = HeatmapScale.NEUTRAL,
        selected = state.selections.selectedZone,
        onZoneTapped = onZoneTapped,
        contentDescription = stringResource(R.string.heatmap_board_description),
        zoneDescription = zoneDescription,
        modifier = modifier,
    )
}

/** HM6: the band shows the selected zone, or "Touchez une zone" when none is. */
@Composable
internal fun HeatmapBand(state: HeatmapSectionState, resources: Resources, locale: Locale) {
    val selected = state.selected
    ZoneBand(
        title = selected?.name?.text(resources) ?: stringResource(HeatmapSectionState.BAND_NONE_RES),
        detail = selected?.detail(resources, locale),
    )
}

/** HM2, HM4: the misses line, with the label of HM4. */
@Composable
internal fun HeatmapMisses(state: HeatmapSectionState, resources: Resources, locale: Locale) {
    FigureRowView(
        FigureRow("heatmap_misses", stringResource(state.missLabelRes), missesText(state, resources, locale)),
    )
}

/** HM2, HM4: the misses with their count and percentage of all darts. */
private fun missesText(state: HeatmapSectionState, resources: Resources, locale: Locale): String {
    val share = state.missShare ?: 0.0
    return resources.getString(
        R.string.heatmap_zone_detail,
        dartsText(resources, state.heatmap.misses),
        formatPercent(share, locale),
    )
}

/** HM6: the 5 most hit zones as text, one line each: "Triple 20" and "12 darts · 4.7 %". */
@Composable
internal fun TopZones(state: HeatmapSectionState, resources: Resources, locale: Locale) {
    val colors = KosmoTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag(HeatmapTags.TOP_LIST)) {
        SectionLabel(stringResource(R.string.heatmap_top_title))
        state.topZones.forEachIndexed { index, figure ->
            FlowRow(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .testTag(HeatmapTags.topRow(index)),
            ) {
                Text(
                    text = figure.name.text(resources),
                    style = KosmoTheme.typography.body,
                    color = colors.onBackgroundMuted,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(
                    text = figure.detail(resources, locale),
                    style = KosmoTheme.typography.bodyLarge,
                    color = colors.onBackground,
                )
            }
        }
    }
}

