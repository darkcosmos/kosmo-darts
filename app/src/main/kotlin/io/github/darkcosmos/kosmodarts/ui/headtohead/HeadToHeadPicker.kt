package io.github.darkcosmos.kosmodarts.ui.headtohead

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.history.PickerProfile
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.HeadToHeadEvent
import io.github.darkcosmos.kosmodarts.ui.profile.profileResources
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States

/**
 * HH1: the partner picker. Every other profile (the shown one and "Deleted player" are not in the list) with its number
 * of common matches, most first; a profile with none is listed and opens the empty state. One row is one 48 dp target.
 */
@Composable
internal fun HeadToHeadPicker(
    ownName: String,
    entries: List<PickerProfile>,
    onEvent: (AppEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Text(
            text = stringResource(R.string.h2h_picker_title, ownName),
            style = KosmoTheme.typography.bodyLarge,
            color = KosmoTheme.colors.onBackground,
            modifier = Modifier
                .testTag(HeadToHeadTags.PICKER_TITLE)
                .semantics { heading() },
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .testTag(HeadToHeadTags.PICKER_LIST),
        ) {
            items(entries, key = { it.id }) { entry ->
                PickerRow(entry) { onEvent(HeadToHeadEvent.PartnerPicked(entry.id)) }
            }
        }
    }
}

@Composable
private fun PickerRow(entry: PickerProfile, onClick: () -> Unit) {
    val colors = KosmoTheme.colors
    val resources = profileResources()
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HeadToHeadTags.pickerRow(entry.id))
            .clip(KosmoShapes.card)
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .sizeIn(minHeight = States.minTouchTarget)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = entry.name, style = KosmoTheme.typography.bodyLarge, color = colors.onBackground)
        Text(
            text = resources.getQuantityString(R.plurals.h2h_common_count, entry.commonCount, entry.commonCount),
            style = KosmoTheme.typography.body,
            color = colors.onBackgroundMuted,
        )
    }
}
