package io.github.darkcosmos.kosmodarts.ui.headtohead

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.HeadToHeadEvent
import io.github.darkcosmos.kosmodarts.ui.components.ButtonKind
import io.github.darkcosmos.kosmodarts.ui.components.KosmoButton
import io.github.darkcosmos.kosmodarts.ui.components.SectionLabel
import io.github.darkcosmos.kosmodarts.ui.history.HistoryScaffold
import io.github.darkcosmos.kosmodarts.ui.history.LoadingIndicator
import io.github.darkcosmos.kosmodarts.ui.layout.ScreenLayoutPolicy
import io.github.darkcosmos.kosmodarts.ui.profile.NoMatchForFilters
import io.github.darkcosmos.kosmodarts.ui.profile.ignoredMatchesText
import io.github.darkcosmos.kosmodarts.ui.profile.profileLocale
import io.github.darkcosmos.kosmodarts.ui.profile.profileResources
import io.github.darkcosmos.kosmodarts.ui.profile.sinceText
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme

/**
 * The Head-to-head screen (HH1 to HH9, HS7, PS8): the partner picker while [partner] is `null`, then the comparison of
 * the two players over their common matches: counters, side-by-side figures, filters, and the list of common matches
 * (a tap opens its detail). Loading, failure and "no common match" are messages; Back stays in the top bar. Everything
 * shown comes from [state]: nothing is counted here (HS9: the state lives in the app state). Portrait: one column.
 * Landscape: header and filters at the left, figures and matches at the right.
 */
@Composable
fun HeadToHeadScreen(
    partner: Long?,
    state: HeadToHeadState,
    onEvent: (AppEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isWide = ScreenLayoutPolicy.hasTwoColumns(
        configuration.screenWidthDp.toFloat(),
        configuration.screenHeightDp.toFloat(),
    )
    HistoryScaffold(
        title = stringResource(R.string.h2h_title),
        onBack = { onEvent(AppEvent.Back) },
        modifier = modifier,
        backTag = HeadToHeadTags.BACK,
        maxWidth = (if (isWide) ScreenLayoutPolicy.TWO_COLUMNS_MAX_WIDTH else ScreenLayoutPolicy.CONTENT_MAX_WIDTH).dp,
    ) {
        val body = Modifier.weight(1f)
        when (state.view(partner)) {
            HeadToHeadView.PICKER_LOADING, HeadToHeadView.PAIR_LOADING ->
                Column(modifier = body.verticalScroll(rememberScrollState())) {
                    LoadingIndicator(R.string.h2h_loading)
                }
            HeadToHeadView.PICKER_FAILED -> FailedMessage(onEvent, canChange = false, modifier = body)
            HeadToHeadView.PAIR_FAILED -> FailedMessage(onEvent, canChange = true, modifier = body)
            HeadToHeadView.PICKER_EMPTY -> EmptyPicker(body)
            HeadToHeadView.PICKER -> HeadToHeadPicker(state.pickerOwnName, state.pickerEntries, onEvent, body)
            HeadToHeadView.NO_COMMON_MATCH -> NoCommonMatch(state, onEvent, body)
            HeadToHeadView.CONTENT ->
                if (isWide) LandscapeContent(state, onEvent, body) else PortraitContent(state, onEvent, body)
        }
    }
}

/** The name of the shown profile, as the picker read gave it (HH1). */
private val HeadToHeadState.pickerOwnName: String
    get() = (picker as? PickerContent.Loaded)?.picker?.name.orEmpty()

@Composable
private fun PortraitContent(state: HeadToHeadState, onEvent: (AppEvent) -> Unit, modifier: Modifier) {
    val result = state.result ?: return
    val nameA = state.nameA.orEmpty()
    val nameB = state.nameB.orEmpty()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.testTag(HeadToHeadTags.CONTENT)) {
        item(key = "header") { PairHeader(state, onEvent) }
        item(key = "filters") { HeadToHeadFilters(state.selections, onEvent) }
        if (state.isSelectionEmpty) {
            item(key = "no_match") { NoMatchForFilters() }
        } else {
            item(key = "figures") { Figures(result, state.selections.mode, nameA, nameB) }
            item(key = "matches_title") { SectionLabel(stringResource(R.string.h2h_matches_title)) }
            items(result.matches, key = { it.sessionId }) { row ->
                CommonMatchCard(row, nameA, nameB) { onEvent(HeadToHeadEvent.MatchTapped(row.sessionId)) }
            }
        }
    }
}

@Composable
private fun LandscapeContent(state: HeadToHeadState, onEvent: (AppEvent) -> Unit, modifier: Modifier) {
    val result = state.result ?: return
    val nameA = state.nameA.orEmpty()
    val nameB = state.nameB.orEmpty()
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(LEFT_WEIGHT)
                .verticalScroll(rememberScrollState()),
        ) {
            PairHeader(state, onEvent)
            HeadToHeadFilters(state.selections, onEvent)
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(RIGHT_WEIGHT)
                .testTag(HeadToHeadTags.CONTENT),
        ) {
            if (state.isSelectionEmpty) {
                item(key = "no_match") { NoMatchForFilters() }
            } else {
                item(key = "figures") { Figures(result, state.selections.mode, nameA, nameB) }
                item(key = "matches_title") { SectionLabel(stringResource(R.string.h2h_matches_title)) }
                items(result.matches, key = { it.sessionId }) { row ->
                    CommonMatchCard(row, nameA, nameB) { onEvent(HeadToHeadEvent.MatchTapped(row.sessionId)) }
                }
            }
        }
    }
}

@Composable
private fun Figures(result: HeadToHeadResult, mode: HeadToHeadMode, nameA: String, nameB: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        compareBlocks(result, mode).forEach { CompareBlockView(it, nameA, nameB) }
    }
}

/**
 * HH5, HH8, ST10: the two names, "Since always" or "Since <date>", the notice of the ignored matches when there is
 * one, and "Change player", which brings the picker back.
 */
@Composable
private fun PairHeader(state: HeadToHeadState, onEvent: (AppEvent) -> Unit) {
    val colors = KosmoTheme.colors
    val resources = profileResources()
    val locale = profileLocale()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.h2h_versus, state.nameA.orEmpty(), state.nameB.orEmpty()),
            style = KosmoTheme.typography.title,
            color = colors.onBackground,
            modifier = Modifier
                .testTag(HeadToHeadTags.TITLE)
                .semantics { heading() },
        )
        state.since?.let { since ->
            Text(
                text = sinceText(resources, since, locale),
                style = KosmoTheme.typography.body,
                color = colors.onBackgroundMuted,
                modifier = Modifier.testTag(HeadToHeadTags.SINCE),
            )
        }
        if (state.isIgnoredNoticeShown) {
            Text(
                text = ignoredMatchesText(resources, state.ignoredMatches),
                style = KosmoTheme.typography.body,
                color = colors.onBackgroundMuted,
                modifier = Modifier.testTag(HeadToHeadTags.IGNORED),
            )
        }
        ChangeButton(onEvent, Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ChangeButton(onEvent: (AppEvent) -> Unit, modifier: Modifier = Modifier) {
    KosmoButton(
        text = stringResource(R.string.h2h_change),
        onClick = { onEvent(HeadToHeadEvent.ChangePartnerTapped) },
        kind = ButtonKind.SECONDARY,
        modifier = modifier
            .fillMaxWidth()
            .testTag(HeadToHeadTags.CHANGE),
    )
}

/** HH9, PS8: the two players have no common match, whatever the mode and the period. */
@Composable
private fun NoCommonMatch(state: HeadToHeadState, onEvent: (AppEvent) -> Unit, modifier: Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        PairHeader(state, onEvent)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .testTag(HeadToHeadTags.NO_COMMON_MATCH),
        ) {
            Text(
                text = stringResource(R.string.h2h_no_common_title),
                style = KosmoTheme.typography.bodyLarge,
                color = KosmoTheme.colors.onBackground,
            )
            Text(
                text = stringResource(R.string.h2h_no_common_hint),
                style = KosmoTheme.typography.body,
                color = KosmoTheme.colors.onBackgroundMuted,
            )
        }
    }
}

/** HH1: there is no other profile to compare with. */
@Composable
private fun EmptyPicker(modifier: Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp)
            .testTag(HeadToHeadTags.PICKER_EMPTY),
    ) {
        Text(
            text = stringResource(R.string.h2h_picker_empty_title),
            style = KosmoTheme.typography.bodyLarge,
            color = KosmoTheme.colors.onBackground,
        )
        Text(
            text = stringResource(R.string.h2h_picker_empty_hint),
            style = KosmoTheme.typography.body,
            color = KosmoTheme.colors.onBackgroundMuted,
        )
    }
}

/** HS7: a short message and "Retry"; a failed pair read also offers "Change player". */
@Composable
private fun FailedMessage(onEvent: (AppEvent) -> Unit, canChange: Boolean, modifier: Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.h2h_failed),
            style = KosmoTheme.typography.bodyLarge,
            color = KosmoTheme.colors.onBackground,
            modifier = Modifier
                .testTag(HeadToHeadTags.FAILED)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        KosmoButton(
            text = stringResource(R.string.profile_retry),
            onClick = { onEvent(HeadToHeadEvent.RetryTapped) },
            kind = ButtonKind.PRIMARY,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HeadToHeadTags.RETRY),
        )
        if (canChange) ChangeButton(onEvent)
    }
}

private const val LEFT_WEIGHT = 0.42f
private const val RIGHT_WEIGHT = 0.58f
