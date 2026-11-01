package io.github.darkcosmos.kosmodarts.ui.history

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.components.ButtonKind
import io.github.darkcosmos.kosmodarts.ui.components.KosmoButton
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/** HS7: nothing is shown for this long while a read runs, then the loading indicator. */
const val LOADING_INDICATOR_DELAY_MILLIS: Long = 300L

/**
 * The History screen (HS1 to HS4, HS6, HS7): the filters, then the list of matches, or one message for the loading,
 * empty archive, no-match and read-failure states. Read-only: no delete button, no popup. Rows are keyed by session so
 * that a long list stays light.
 */
@Composable
fun HistoryScreen(state: HistoryState, onEvent: (AppEvent) -> Unit, modifier: Modifier = Modifier) {
    val chipsScroll = rememberScrollState()
    HistoryScaffold(
        title = stringResource(R.string.history_title),
        onBack = { onEvent(AppEvent.Back) },
        modifier = modifier,
    ) {
        if (state.view == HistoryView.ROWS) {
            // A new read starts a new list, so that the scroll position of the new filters is not the old one (HS6).
            key(state.loadId) { MatchList(state, chipsScroll, onEvent, Modifier.weight(1f)) }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                HistoryFiltersBar(state.filters.mode, state.filters.profileId, state.profiles, chipsScroll, onEvent)
                StateMessage(state.view, onEvent)
            }
        }
    }
}

/** The filters scroll with the matches (landscape and large fonts keep room for rows); keys are session ids. */
@Composable
private fun MatchList(state: HistoryState, chipsScroll: ScrollState, onEvent: (AppEvent) -> Unit, modifier: Modifier) {
    val matches = (state.content as HistoryContent.Loaded).matches
    val listState = rememberLazyListState(state.scroll.index, state.scroll.offset)
    val currentOnEvent by rememberUpdatedState(onEvent)
    // The position is reported when a scroll stops, not at each frame (HS6);
    // the idle list at the start reports nothing.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.drop(1).filter { !it }.collect {
            val index = listState.firstVisibleItemIndex
            currentOnEvent(AppEvent.HistoryScrolled(index, listState.firstVisibleItemScrollOffset))
        }
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.testTag(HistoryTags.LIST),
    ) {
        item(key = FILTERS_KEY, contentType = FILTERS_KEY) {
            HistoryFiltersBar(state.filters.mode, state.filters.profileId, state.profiles, chipsScroll, onEvent)
        }
        items(matches, key = { it.sessionId }, contentType = { ROW_TYPE }) { match ->
            HistoryRowCard(match, onClick = { onEvent(AppEvent.HistoryMatchTapped(match.sessionId)) })
        }
    }
}

@Composable
private fun StateMessage(view: HistoryView, onEvent: (AppEvent) -> Unit) {
    when (view) {
        HistoryView.LOADING -> LoadingIndicator()
        HistoryView.FAILED -> FailedMessage(onEvent)
        HistoryView.EMPTY_ARCHIVE -> EmptyMessage()
        HistoryView.NO_MATCH -> NoMatchMessage(onEvent)
        HistoryView.ROWS -> Unit
    }
}

/** HS7: nothing for the first 300 ms, so a quick read never flashes; then an indicator TalkBack can describe. */
@Composable
private fun LoadingIndicator() {
    var isShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADING_INDICATOR_DELAY_MILLIS)
        isShown = true
    }
    if (!isShown) return
    val description = stringResource(R.string.history_loading)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)) {
        CircularProgressIndicator(
            color = KosmoTheme.colors.accent,
            modifier = Modifier
                .testTag(HistoryTags.LOADING)
                .semantics { contentDescription = description },
        )
    }
}

/** HS4: nothing was ever archived. */
@Composable
private fun EmptyMessage() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .testTag(HistoryTags.EMPTY),
    ) {
        Text(
            text = stringResource(R.string.history_empty_title),
            style = KosmoTheme.typography.bodyLarge,
            color = KosmoTheme.colors.onBackground,
        )
        Text(
            text = stringResource(R.string.history_empty_hint),
            style = KosmoTheme.typography.body,
            color = KosmoTheme.colors.onBackgroundMuted,
        )
    }
}

/** HS4: matches exist, none matches the filters, which stay shown; "Show all matches" resets both. */
@Composable
private fun NoMatchMessage(onEvent: (AppEvent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.history_no_match),
            style = KosmoTheme.typography.bodyLarge,
            color = KosmoTheme.colors.onBackground,
            modifier = Modifier.testTag(HistoryTags.NO_MATCH),
        )
        KosmoButton(
            text = stringResource(R.string.history_show_all),
            onClick = { onEvent(AppEvent.HistoryShowAllTapped) },
            kind = ButtonKind.SECONDARY,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HistoryTags.SHOW_ALL),
        )
    }
}

/** HS7: a short message in the player's language and "Retry"; the Back action stays in the top bar. */
@Composable
private fun FailedMessage(onEvent: (AppEvent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.history_failed),
            style = KosmoTheme.typography.bodyLarge,
            color = KosmoTheme.colors.onBackground,
            modifier = Modifier
                .testTag(HistoryTags.FAILED)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        KosmoButton(
            text = stringResource(R.string.history_retry),
            onClick = { onEvent(AppEvent.HistoryRetryTapped) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HistoryTags.RETRY),
        )
    }
}

private const val FILTERS_KEY = "filters"
private const val ROW_TYPE = "row"
