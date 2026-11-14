package io.github.darkcosmos.kosmodarts.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.ProfileEvent
import io.github.darkcosmos.kosmodarts.ui.components.ButtonKind
import io.github.darkcosmos.kosmodarts.ui.components.KosmoButton
import io.github.darkcosmos.kosmodarts.ui.components.WordSafeText
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States

private val TAB_UNDERLINE = 3.dp

/**
 * PS2: the name, "Since always" or "Since <date>" (ST9), the notice of the ignored matches (ST10) when there is one,
 * the "Compare with..." button (HH1) and the "Manage player" button with its actions (PS7).
 */
@Composable
internal fun ProfileHeader(state: ProfileState, onEvent: (AppEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = KosmoTheme.colors
    val resources = profileResources()
    val locale = profileLocale()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.fillMaxWidth()) {
        state.name?.let { name ->
            Text(
                text = name,
                style = KosmoTheme.typography.title,
                color = colors.onBackground,
                modifier = Modifier
                    .testTag(ProfileTags.NAME)
                    .semantics { heading() },
            )
        }
        state.since?.let { since ->
            Text(
                text = sinceText(resources, since, locale),
                style = KosmoTheme.typography.body,
                color = colors.onBackgroundMuted,
                modifier = Modifier.testTag(ProfileTags.SINCE),
            )
        }
        if (state.isIgnoredNoticeShown) {
            Text(
                text = ignoredMatchesText(resources, state.ignoredMatches),
                style = KosmoTheme.typography.body,
                color = colors.onBackgroundMuted,
                modifier = Modifier.testTag(ProfileTags.IGNORED),
            )
        }
        if (state.name != null) CompareButton(state, onEvent)
        ManageActions(state, onEvent)
    }
}

/** PS2, HH1: "Compare with..." opens the Head-to-head of this profile, with its picker. */
@Composable
private fun CompareButton(state: ProfileState, onEvent: (AppEvent) -> Unit) {
    KosmoButton(
        text = stringResource(R.string.profile_compare),
        onClick = { onEvent(ProfileEvent.CompareTapped) },
        kind = ButtonKind.SECONDARY,
        enabled = !state.isBusy,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag(ProfileTags.COMPARE),
    )
}

/**
 * P1 A, PM11: the "Manage player" button under the name unfolds the four actions inside the screen, never a pop-up.
 * Available once the profile is known (the roster read with it). A refusal or an error is told under the button.
 */
@Composable
private fun ManageActions(state: ProfileState, onEvent: (AppEvent) -> Unit) {
    val profile = (state.roster as? ProfileRoster.Loaded)?.self ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        KosmoButton(
            text = stringResource(R.string.profile_manage),
            onClick = { onEvent(ProfileEvent.ManageToggled) },
            kind = if (state.isManageOpen) ButtonKind.OUTLINED_ACCENT else ButtonKind.SECONDARY,
            enabled = !state.isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ProfileTags.MANAGE),
        )
        state.message?.let { ProfileMessageText(it) }
        if (state.isManageOpen) {
            Column(modifier = Modifier.testTag(ProfileTags.ACTIONS_PANEL)) {
                ProfileActionsPanelContent(profile, state, onEvent)
            }
        }
    }
}

/**
 * PS3: the three sections as tabs, equal widths, at least 48 dp high (they grow with the text at a large font), the
 * selected one underlined in the accent colour. Each tab is a Tab for TalkBack with its selected state.
 */
@Composable
internal fun ProfileTabs(selected: ProfileSection, onSelect: (ProfileSection) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(KosmoTheme.colors.background)
            .height(IntrinsicSize.Min)
            .selectableGroup(),
    ) {
        ProfileSection.entries.forEach { section ->
            ProfileTab(
                label = tabLabel(section),
                isSelected = section == selected,
                onClick = { onSelect(section) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(ProfileTags.tab(section)),
            )
        }
    }
}

@Composable
private fun ProfileTab(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = KosmoTheme.colors
    Column(
        modifier = modifier
            .heightIn(min = States.minTouchTarget)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth()) {
            // A label is never cut inside a word (U11): at a large font "HEATMAP" gets smaller instead.
            Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                WordSafeText(
                    text = label,
                    style = KosmoTheme.typography.button.copy(
                        color = if (isSelected) colors.accent else colors.onBackgroundMuted,
                    ),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TAB_UNDERLINE)
                .background(if (isSelected) colors.accent else colors.outline),
        )
    }
}

@Composable
private fun tabLabel(section: ProfileSection): String = when (section) {
    ProfileSection.X01 -> stringResource(R.string.profile_tab_x01)
    ProfileSection.CRICKET -> stringResource(R.string.cricket_title)
    ProfileSection.HEATMAP -> stringResource(R.string.profile_tab_heatmap)
}
