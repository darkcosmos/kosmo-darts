package io.github.darkcosmos.kosmodarts.ui.history

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
import io.github.darkcosmos.kosmodarts.history.HistoryProfile
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.components.SectionLabel
import io.github.darkcosmos.kosmodarts.ui.components.SegmentedChoice
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States

private val CHIP_MAX_WIDTH = 220.dp
private val MODE_OPTIONS: List<ArchivedMode?> = listOf(null, ArchivedMode.X01, ArchivedMode.CRICKET)

/**
 * The two filters of History (HS3), shown in every state (HS4): the mode as a segmented choice (All, X01, Cricket)
 * and the player as a scrollable row of chips (All, then one per profile). Selected values are exposed to TalkBack;
 * the selected chip is scrolled into view. [chipsScroll] lives above, so that it survives a change of state.
 */
@Composable
fun HistoryFiltersBar(
    mode: ArchivedMode?,
    profileId: Long?,
    profiles: List<HistoryProfile>,
    chipsScroll: ScrollState,
    onEvent: (AppEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.history_filter_mode))
        SegmentedChoice(
            options = MODE_OPTIONS,
            selected = mode,
            label = { modeLabel(it) },
            onSelect = { onEvent(AppEvent.HistoryModeSelected(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HistoryTags.MODE_FILTER),
        )
        SectionLabel(stringResource(R.string.history_filter_player), Modifier.padding(top = 8.dp))
        PlayerChips(profileId, profiles, chipsScroll, onEvent)
    }
}

@Composable
private fun modeLabel(mode: ArchivedMode?): String = when (mode) {
    null -> stringResource(R.string.history_filter_all)
    ArchivedMode.X01 -> stringResource(R.string.mode_x01_title)
    ArchivedMode.CRICKET -> stringResource(R.string.cricket_title)
}

@Composable
private fun PlayerChips(
    profileId: Long?,
    profiles: List<HistoryProfile>,
    scroll: ScrollState,
    onEvent: (AppEvent) -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    // The selected chip may be far to the right (many profiles): bring it into view, also after a rotation (HS6).
    LaunchedEffect(profileId, profiles) { requester.bringIntoView() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .horizontalScroll(scroll)
            .selectableGroup(),
    ) {
        FilterChip(
            label = stringResource(R.string.history_filter_all),
            isSelected = profileId == null,
            onClick = { onEvent(AppEvent.HistoryProfileSelected(null)) },
            modifier = Modifier
                .testTag(HistoryTags.PLAYER_ALL)
                .selectedChip(profileId == null, requester),
        )
        profiles.forEach { profile ->
            val isSelected = profile.id == profileId
            FilterChip(
                label = profile.name,
                isSelected = isSelected,
                onClick = { onEvent(AppEvent.HistoryProfileSelected(profile.id)) },
                modifier = Modifier
                    .testTag(HistoryTags.playerChip(profile.id))
                    .selectedChip(isSelected, requester),
            )
        }
    }
}

private fun Modifier.selectedChip(isSelected: Boolean, requester: BringIntoViewRequester): Modifier =
    if (isSelected) bringIntoViewRequester(requester) else this

/** A chip of the player filter: at least 48 dp high and wide, a radio button for TalkBack with its selected state. */
@Composable
private fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = KosmoTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .sizeIn(minWidth = States.minTouchTarget, minHeight = States.minTouchTarget)
            .clip(KosmoShapes.control)
            .background(if (isSelected) colors.accent else colors.surface)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = KosmoTheme.typography.body,
            color = if (isSelected) colors.onAccent else colors.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = CHIP_MAX_WIDTH),
        )
    }
}
