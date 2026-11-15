package io.github.darkcosmos.kosmodarts.ui.players

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.history.PlayerSummary
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.PlayersEvent
import io.github.darkcosmos.kosmodarts.ui.components.KosmoButton
import io.github.darkcosmos.kosmodarts.ui.history.HistoryScaffold
import io.github.darkcosmos.kosmodarts.ui.history.LoadingIndicator
import io.github.darkcosmos.kosmodarts.ui.history.formatEndDate
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States
import java.time.ZoneId

/**
 * The Players screen (PM2): the profiles most recently used first, each row opening the Profile, where the actions are
 * (PS7). It only shows the [PlayersState] and sends events.
 */
@Composable
fun PlayersScreen(state: PlayersState, onEvent: (AppEvent) -> Unit, modifier: Modifier = Modifier) {
    HistoryScaffold(
        title = stringResource(R.string.players_title),
        onBack = { onEvent(AppEvent.Back) },
        modifier = modifier,
        backTag = PlayersTags.BACK,
    ) {
        when (state.view) {
            PlayersView.LIST -> PlayersList(state, onEvent, Modifier.weight(1f))
            PlayersView.LOADING -> LoadingIndicator(R.string.players_loading)
            PlayersView.FAILED -> StateColumn(Modifier.weight(1f)) { FailedMessage(onEvent) }
            PlayersView.EMPTY -> StateColumn(Modifier.weight(1f)) { EmptyMessage() }
        }
    }
}

@Composable
private fun StateColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) { content() }
}

@Composable
private fun PlayersList(state: PlayersState, onEvent: (AppEvent) -> Unit, modifier: Modifier) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.testTag(PlayersTags.LIST),
    ) {
        items(state.players, key = { it.id }) { profile -> PlayerCard(profile, onEvent) }
    }
}

/** A row: name, number of matches and last match date; the whole row opens the Profile (PM2, PS1). */
@Composable
private fun PlayerCard(profile: PlayerSummary, onEvent: (AppEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KosmoShapes.card)
            .background(KosmoTheme.colors.surface)
            .testTag(PlayersTags.card(profile.id)),
    ) {
        CardHeader(profile) { onEvent(PlayersEvent.ProfileTapped(profile.id)) }
    }
}

@Composable
private fun CardHeader(profile: PlayerSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = States.minTouchTarget)
            .clickable(
                onClickLabel = stringResource(R.string.profile_open, profile.name),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = profile.name,
            style = KosmoTheme.typography.bodyLarge,
            color = KosmoTheme.colors.onBackground,
        )
        Text(
            text = summaryText(profile),
            style = KosmoTheme.typography.body,
            color = KosmoTheme.colors.onBackgroundMuted,
            modifier = Modifier.testTag(PlayersTags.summary(profile.id)),
        )
    }
}

/** PM2: maps the [PlayerSummaryText] of the profile to its resources. */
@Composable
private fun summaryText(profile: PlayerSummary): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (val text = profile.summaryText()) {
        PlayerSummaryText.NoMatch -> stringResource(R.string.players_no_match)
        is PlayerSummaryText.Matches -> pluralStringResource(R.plurals.players_match_count, text.count, text.count)
        is PlayerSummaryText.MatchesAndLast ->
            pluralStringResource(R.plurals.players_match_count, text.count, text.count) +
                stringResource(R.string.format_separator) +
                stringResource(
                    R.string.players_last_match,
                    formatEndDate(text.lastEndedAt, locale, ZoneId.systemDefault()),
                )
    }
}

/** HS7 form: a short message and "Retry". */
@Composable
private fun FailedMessage(onEvent: (AppEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 24.dp)) {
        Text(
            text = stringResource(R.string.players_failed),
            style = KosmoTheme.typography.bodyLarge,
            color = KosmoTheme.colors.onBackground,
            modifier = Modifier
                .testTag(PlayersTags.FAILED)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        KosmoButton(
            text = stringResource(R.string.players_retry),
            onClick = { onEvent(PlayersEvent.RetryTapped) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PlayersTags.RETRY),
        )
    }
}

/** PM2: no profile. */
@Composable
private fun EmptyMessage() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(vertical = 24.dp)
            .testTag(PlayersTags.EMPTY),
    ) {
        Text(
            text = stringResource(R.string.players_empty_title),
            style = KosmoTheme.typography.bodyLarge,
            color = KosmoTheme.colors.onBackground,
        )
        Text(
            text = stringResource(R.string.players_empty_hint),
            style = KosmoTheme.typography.body,
            color = KosmoTheme.colors.onBackgroundMuted,
        )
    }
}
