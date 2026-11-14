package io.github.darkcosmos.kosmodarts.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.ProfileEvent
import io.github.darkcosmos.kosmodarts.ui.components.ButtonKind
import io.github.darkcosmos.kosmodarts.ui.components.KosmoButton
import io.github.darkcosmos.kosmodarts.ui.history.LoadingIndicator
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme

/** PS6: the profile has matches, none for the filters or the period of the section, which stay shown. */
@Composable
internal fun NoMatchForFilters() {
    Text(
        text = stringResource(R.string.profile_no_match_filters),
        style = KosmoTheme.typography.bodyLarge,
        color = KosmoTheme.colors.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .testTag(ProfileTags.NO_MATCH_FILTERS),
    )
}

/** Loading, read failure and "no match at all" (PS6, HS7): the Back action stays in the top bar. */
@Composable
internal fun MessageScreen(state: ProfileState, onEvent: (AppEvent) -> Unit, modifier: Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        when (state.view) {
            ProfileView.LOADING -> LoadingIndicator(R.string.profile_loading)
            ProfileView.FAILED -> FailedMessage(onEvent)
            ProfileView.NO_MATCH -> {
                ProfileHeader(state, onEvent)
                EmptyMessage()
            }
            ProfileView.CONTENT -> Unit
        }
    }
}

/** PS6: this player has no finished match at all. */
@Composable
private fun EmptyMessage() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .testTag(ProfileTags.EMPTY),
    ) {
        Text(
            text = stringResource(R.string.profile_empty_title),
            style = KosmoTheme.typography.bodyLarge,
            color = KosmoTheme.colors.onBackground,
        )
        Text(
            text = stringResource(R.string.profile_empty_hint),
            style = KosmoTheme.typography.body,
            color = KosmoTheme.colors.onBackgroundMuted,
        )
    }
}

/** HS7: a short message and "Retry". */
@Composable
private fun FailedMessage(onEvent: (AppEvent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_failed),
            style = KosmoTheme.typography.bodyLarge,
            color = KosmoTheme.colors.onBackground,
            modifier = Modifier
                .testTag(ProfileTags.FAILED)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        KosmoButton(
            text = stringResource(R.string.profile_retry),
            onClick = { onEvent(ProfileEvent.RetryTapped) },
            kind = ButtonKind.PRIMARY,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ProfileTags.RETRY),
        )
    }
}
