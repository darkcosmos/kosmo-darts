package io.github.darkcosmos.kosmodarts.ui.players

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.player.PlayerListError
import io.github.darkcosmos.kosmodarts.engine.player.PlayerListRules
import io.github.darkcosmos.kosmodarts.history.PlayerSummary
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.PlayersEvent
import io.github.darkcosmos.kosmodarts.ui.components.ButtonKind
import io.github.darkcosmos.kosmodarts.ui.components.KosmoButton
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States

/** From this font scale on, the buttons of a row are stacked: two labels no longer fit side by side (U4, U6). */
private const val STACK_FONT_SCALE = 1.3f

/** The gap between the three harmless actions and Delete (U4). */
private val DELETE_GAP = 16.dp

/** What the open card shows under its header: the four actions, or the panel of the action chosen (PM3 to PM6). */
@Composable
internal fun PlayersPanelContent(profile: PlayerSummary, state: PlayersState, onEvent: (AppEvent) -> Unit) {
    val enabled = !state.isBusy
    when (val panel = state.panel) {
        null -> ActionsGrid(enabled, onEvent)
        is PlayersPanel.Rename -> RenamePanel(panel, enabled, onEvent)
        PlayersPanel.MergePick -> MergePickPanel(profile, state.mergeTargets, enabled, onEvent)
        is PlayersPanel.MergeConfirm -> ConfirmPanel(
            text = stringResource(R.string.players_merge_confirm_text, profile.name, panel.target.name),
            confirmLabel = stringResource(R.string.players_merge),
            kind = ButtonKind.PRIMARY,
            enabled = enabled,
            onEvent = onEvent,
        )
        PlayersPanel.ResetConfirm -> ConfirmPanel(
            text = stringResource(R.string.players_reset_confirm_text, profile.name),
            confirmLabel = stringResource(R.string.players_reset),
            kind = ButtonKind.PRIMARY,
            enabled = enabled,
            onEvent = onEvent,
        )
        is PlayersPanel.DeleteConfirm -> ConfirmPanel(
            text = deleteText(profile.name, panel.count),
            confirmLabel = stringResource(R.string.players_delete),
            kind = ButtonKind.DESTRUCTIVE,
            enabled = enabled,
            onEvent = onEvent,
            isDanger = true,
        )
    }
}

/** PM6: the confirmation gives the number of matches erased; "No match will be erased" when there is none. */
@Composable
private fun deleteText(name: String, count: Int): String {
    val erased = when (val text = erasedText(count)) {
        ErasedText.None -> stringResource(R.string.players_delete_erased_none)
        is ErasedText.Matches -> pluralStringResource(R.plurals.players_delete_erased, text.count, text.count)
    }
    return stringResource(R.string.players_delete_confirm_text, name, erased)
}

private val stackedButtons: Boolean
    @Composable get() = LocalDensity.current.fontScale >= STACK_FONT_SCALE

/** Rename and Merge on the first row, Reset on the second, Delete alone on the last, 16dp apart (U4). */
@Composable
private fun ActionsGrid(enabled: Boolean, onEvent: (AppEvent) -> Unit) {
    val rename = stringResource(R.string.players_rename)
    val merge = stringResource(R.string.players_merge)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (stackedButtons) {
            ActionButton(rename, PlayersTags.RENAME, enabled, Modifier) { onEvent(PlayersEvent.RenameTapped) }
            ActionButton(merge, PlayersTags.MERGE, enabled, Modifier) { onEvent(PlayersEvent.MergeTapped) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(rename, PlayersTags.RENAME, enabled, Modifier.weight(1f)) {
                    onEvent(PlayersEvent.RenameTapped)
                }
                ActionButton(merge, PlayersTags.MERGE, enabled, Modifier.weight(1f)) {
                    onEvent(PlayersEvent.MergeTapped)
                }
            }
        }
        ActionButton(stringResource(R.string.players_reset), PlayersTags.RESET, enabled, Modifier) {
            onEvent(PlayersEvent.ResetTapped)
        }
        KosmoButton(
            text = stringResource(R.string.players_delete),
            onClick = { onEvent(PlayersEvent.DeleteTapped) },
            kind = ButtonKind.DESTRUCTIVE,
            enabled = enabled,
            modifier = Modifier
                .padding(top = DELETE_GAP - 8.dp)
                .fillMaxWidth()
                .testTag(PlayersTags.DELETE),
        )
    }
}

@Composable
private fun ActionButton(text: String, tag: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    KosmoButton(
        text = text,
        onClick = onClick,
        kind = ButtonKind.SECONDARY,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .testTag(tag),
    )
}

/** PM3: the name field with its IME action, the error under it, and "Merge into ..." when the name is taken. */
@Composable
private fun RenamePanel(panel: PlayersPanel.Rename, enabled: Boolean, onEvent: (AppEvent) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val taken = (panel.error as? RenameError.Taken)?.target
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RenameField(panel, onEvent, Modifier.focusRequester(focus))
        if (taken != null) {
            KosmoButton(
                text = stringResource(R.string.players_merge_into, taken.name),
                onClick = { onEvent(PlayersEvent.MergeIntoTakenTapped) },
                kind = ButtonKind.OUTLINED_ACCENT,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PlayersTags.MERGE_INTO_TAKEN),
            )
        }
        ButtonPair(
            confirm = stringResource(R.string.players_save),
            confirmTag = PlayersTags.RENAME_SAVE,
            kind = ButtonKind.PRIMARY,
            enabled = enabled,
            onEvent = onEvent,
            onConfirm = PlayersEvent.RenameSaved,
        )
    }
}

@Composable
private fun RenameField(panel: PlayersPanel.Rename, onEvent: (AppEvent) -> Unit, modifier: Modifier) {
    val colors = KosmoTheme.colors
    val error = panel.error?.let { renameErrorText(it) }
    TextField(
        value = panel.text,
        onValueChange = { onEvent(PlayersEvent.RenameTextChanged(it)) },
        label = { Text(stringResource(R.string.players_rename_label)) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let {
            {
                Text(
                    text = it,
                    modifier = Modifier
                        .testTag(PlayersTags.RENAME_ERROR)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        },
        textStyle = KosmoTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onEvent(PlayersEvent.RenameSaved) }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceHigh,
            unfocusedContainerColor = colors.surfaceHigh,
            errorContainerColor = colors.surfaceHigh,
            focusedIndicatorColor = colors.accent,
            unfocusedIndicatorColor = Color.Transparent,
            // Red is for BUST only (design system, principle 4): an error is marked in accent and told in words.
            errorIndicatorColor = colors.accent,
            focusedTextColor = colors.onBackground,
            unfocusedTextColor = colors.onBackground,
            errorTextColor = colors.onBackground,
            cursorColor = colors.accent,
            errorCursorColor = colors.accent,
            focusedLabelColor = colors.onBackgroundMuted,
            unfocusedLabelColor = colors.onBackgroundMuted,
            errorLabelColor = colors.onBackgroundMuted,
            errorSupportingTextColor = colors.onBackground,
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag(PlayersTags.RENAME_FIELD),
    )
}

@Composable
private fun renameErrorText(error: RenameError): String = when (error) {
    is RenameError.Taken -> stringResource(R.string.players_error_name_taken, error.target.name)
    is RenameError.Invalid -> when (error.violation) {
        PlayerListError.BLANK_NAME -> stringResource(R.string.setup_error_blank_name)
        PlayerListError.NAME_TOO_LONG ->
            stringResource(R.string.setup_error_name_too_long, PlayerListRules.MAX_NAME_LENGTH)
        else -> stringResource(R.string.players_error_name_invalid)
    }
}

/** PM4: "Merge Kevin into:", Cancel (reachable without scrolling), then the other profiles as rows. */
@Composable
private fun MergePickPanel(
    profile: PlayerSummary,
    targets: List<PlayerSummary>,
    enabled: Boolean,
    onEvent: (AppEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.players_merge_pick, profile.name),
            style = KosmoTheme.typography.bodyLarge,
            color = KosmoTheme.colors.onBackground,
            modifier = Modifier.testTag(PlayersTags.MERGE_PICK),
        )
        KosmoButton(
            text = stringResource(R.string.players_cancel),
            onClick = { onEvent(PlayersEvent.Cancelled) },
            kind = ButtonKind.SECONDARY,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PlayersTags.CANCEL),
        )
        targets.forEach { target ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = States.minTouchTarget)
                    .clip(KosmoShapes.control)
                    .background(KosmoTheme.colors.surfaceHigh)
                    .clickable(enabled = enabled, role = Role.Button) {
                        onEvent(PlayersEvent.MergeTargetPicked(target.id))
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag(PlayersTags.mergeTarget(target.id)),
            ) {
                Text(target.name, style = KosmoTheme.typography.bodyLarge, color = KosmoTheme.colors.onBackground)
            }
        }
    }
}

/** PM4 to PM6: the consequence in words (a live region), then Cancel and the confirm button. */
@Composable
private fun ConfirmPanel(
    text: String,
    confirmLabel: String,
    kind: ButtonKind,
    enabled: Boolean,
    onEvent: (AppEvent) -> Unit,
    isDanger: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = text,
            style = KosmoTheme.typography.body,
            color = if (isDanger) KosmoTheme.colors.dangerText else KosmoTheme.colors.onBackground,
            modifier = Modifier
                .testTag(PlayersTags.CONFIRM_TEXT)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        ButtonPair(confirmLabel, PlayersTags.CONFIRM, kind, enabled, onEvent, PlayersEvent.Confirmed)
    }
}

/** "Cancel" and the confirm button: side by side, or stacked (confirm first) when the font is large. */
@Composable
private fun ButtonPair(
    confirm: String,
    confirmTag: String,
    kind: ButtonKind,
    enabled: Boolean,
    onEvent: (AppEvent) -> Unit,
    onConfirm: PlayersEvent,
) {
    val cancelButton: @Composable (Modifier) -> Unit = { modifier ->
        KosmoButton(
            text = stringResource(R.string.players_cancel),
            onClick = { onEvent(PlayersEvent.Cancelled) },
            kind = ButtonKind.SECONDARY,
            enabled = enabled,
            modifier = modifier
                .fillMaxWidth()
                .testTag(PlayersTags.CANCEL),
        )
    }
    val confirmButton: @Composable (Modifier) -> Unit = { modifier ->
        KosmoButton(
            text = confirm,
            onClick = { onEvent(onConfirm) },
            kind = kind,
            enabled = enabled,
            modifier = modifier
                .fillMaxWidth()
                .testTag(confirmTag),
        )
    }
    if (stackedButtons) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            confirmButton(Modifier)
            cancelButton(Modifier)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cancelButton(Modifier.weight(1f))
            confirmButton(Modifier.weight(1f))
        }
    }
}
