package io.github.darkcosmos.kosmodarts.capture.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.capture.R
import io.github.darkcosmos.kosmodarts.capture.truth.VideoQuality
import io.github.darkcosmos.kosmodarts.capture.ui.keypad.Keypad
import io.github.darkcosmos.kosmodarts.capture.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.capture.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.capture.ui.theme.States

/** Height of buttons and keys in landscape (CLAUDE.md exception, owner decision Q14). */
internal val CONTROL_HEIGHT = 42.dp

private const val PREVIEW_COLUMN_WEIGHT = 0.55f
private const val KEYPAD_COLUMN_WEIGHT = 0.45f
private val KEY_GAP = 4.dp
private val SPACING = 8.dp

/**
 * The single landscape screen of Kosmo Capture (rules CP2, CP3, CP6, CP7, CP8, CP13). It renders [state] and forwards
 * events to [actions]; the camera preview is supplied by the caller through [preview].
 */
@Composable
fun CaptureScreen(
    state: CaptureUiState,
    actions: CaptureActions,
    preview: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    PhotoFailureMessage(state.photoFailure != null, snackbar, actions.onPhotoMessageShown)
    Box(modifier = modifier.fillMaxSize().background(KosmoTheme.colors.background)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = SPACING, vertical = KEY_GAP),
            horizontalArrangement = Arrangement.spacedBy(SPACING),
        ) {
            PreviewColumn(state, actions, preview, Modifier.weight(PREVIEW_COLUMN_WEIGHT))
            KeypadColumn(state, actions, Modifier.weight(KEYPAD_COLUMN_WEIGHT))
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter)) { data ->
            PhotoSnackbar(data.visuals.message, data.visuals.actionLabel.orEmpty(), data::dismiss)
        }
    }
    if (state.stopConfirmVisible) StopConfirmDialog(actions.onStopConfirmed, actions.onStopCancelled)
    if (state.deleteAllConfirmVisible) DeleteAllDialog(actions.onDeleteAllConfirmed, actions.onDeleteAllCancelled)
}

/** Rule CP13: the message is a live region, stays at least 4 s (short duration with an action) and has a dismiss. */
@Composable
private fun PhotoFailureMessage(failed: Boolean, host: SnackbarHostState, onShown: () -> Unit) {
    val message = stringResource(R.string.photo_failure)
    val dismiss = stringResource(R.string.photo_failure_dismiss)
    LaunchedEffect(failed) {
        if (failed) {
            host.showSnackbar(message = message, actionLabel = dismiss, duration = SnackbarDuration.Short)
            onShown()
        }
    }
}

@Composable
private fun PhotoSnackbar(message: String, dismissLabel: String, onDismiss: () -> Unit) {
    Snackbar(
        modifier = Modifier.padding(SPACING).semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(CaptureTags.PHOTO_SNACKBAR),
        action = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minWidth = States.minTouchTarget, minHeight = CONTROL_HEIGHT)
                    .testTag(CaptureTags.PHOTO_SNACKBAR_DISMISS),
            ) { Text(dismissLabel) }
        },
    ) { Text(message) }
}

@Composable
private fun PreviewColumn(
    state: CaptureUiState,
    actions: CaptureActions,
    preview: @Composable () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
        StatusLine(state)
        if (state.error != null) ErrorMessage(state.error, actions.onErrorDismissed)
        val previewDescription = stringResource(R.string.preview_description)
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .background(KosmoTheme.colors.surface, KosmoShapes.control)
                .semantics { contentDescription = previewDescription }
                .testTag(CaptureTags.PREVIEW),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.permission != CameraPermission.GRANTED ->
                    PermissionPanel(state.permission, actions.onAllowCamera, actions.onOpenSettings)
                else -> {
                    preview()
                    if (!state.cameraReady) CameraStarting()
                }
            }
        }
        ControlRow(state, actions)
    }
}

@Composable
private fun CameraStarting() {
    Text(
        text = stringResource(R.string.camera_starting),
        style = KosmoTheme.typography.body,
        color = KosmoTheme.colors.onBackgroundMuted,
    )
}

@Composable
private fun ErrorMessage(error: CaptureError, onDismiss: () -> Unit) {
    val message = if (error == CaptureError.DELETE_ALL) R.string.error_delete_all else R.string.error_saving
    Row(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(message),
            style = KosmoTheme.typography.body,
            color = KosmoTheme.colors.danger,
            modifier = Modifier.weight(1f).testTag(CaptureTags.ERROR_MESSAGE),
        )
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.defaultMinSize(minWidth = States.minTouchTarget, minHeight = CONTROL_HEIGHT)
                .testTag(CaptureTags.ERROR_DISMISS),
        ) { Text(stringResource(R.string.error_dismiss)) }
    }
}

/** REC dot and label while recording (rule CP2), saving notice, and the two counters of rule CP7. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusLine(state: CaptureUiState) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag(CaptureTags.STATUS_LINE),
        horizontalArrangement = Arrangement.spacedBy(SPACING),
        verticalArrangement = Arrangement.spacedBy(KEY_GAP),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.phase == CapturePhase.RUNNING) RecIndicator()
        if (state.phase == CapturePhase.SAVING) {
            Text(
                text = stringResource(R.string.saving),
                style = KosmoTheme.typography.label,
                color = KosmoTheme.colors.onBackgroundMuted,
                modifier = Modifier.testTag(CaptureTags.SAVING),
            )
        }
        Text(
            text = pluralStringResource(R.plurals.session_count, state.sessionCount, state.sessionCount),
            style = KosmoTheme.typography.label,
            color = KosmoTheme.colors.onBackground,
            modifier = Modifier.testTag(CaptureTags.SESSION_COUNT),
        )
        Text(
            text = pluralStringResource(R.plurals.total_count, state.totalCount, state.totalCount),
            style = KosmoTheme.typography.label,
            color = KosmoTheme.colors.onBackgroundMuted,
            modifier = Modifier.testTag(CaptureTags.TOTAL_COUNT),
        )
    }
}

@Composable
private fun RecIndicator() {
    val description = stringResource(R.string.rec_description)
    Row(
        modifier = Modifier.testTag(CaptureTags.REC).clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP),
    ) {
        Box(Modifier.size(12.dp).background(KosmoTheme.colors.danger, CircleShape))
        Text(
            text = stringResource(R.string.rec_label),
            style = KosmoTheme.typography.label,
            color = KosmoTheme.colors.danger,
        )
    }
}

/** Delete all at the far left, away from the keypad (Q16), the quality toggle, and Start or Stop at the right end. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ControlRow(state: CaptureUiState, actions: CaptureActions) {
    val deleteDescription = stringResource(R.string.delete_all_description)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(KEY_GAP),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = actions.onDeleteAllRequested,
            enabled = state.deleteAllEnabled,
            contentPadding = BUTTON_PADDING,
            modifier = Modifier.controlSize().semantics { contentDescription = deleteDescription }
                .testTag(CaptureTags.DELETE_ALL),
        ) { Text(stringResource(R.string.delete_all), style = KosmoTheme.typography.button) }
        QualityToggle(state, actions.onQuality, Modifier)
        StartStopButton(state, actions)
    }
}

@Composable
private fun StartStopButton(state: CaptureUiState, actions: CaptureActions) {
    val running = state.phase == CapturePhase.RUNNING
    val modifier = Modifier.controlSize().testTag(CaptureTags.START_STOP)
    if (running) {
        OutlinedButton(
            onClick = actions.onStopRequested,
            enabled = state.startStopEnabled,
            contentPadding = BUTTON_PADDING,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = KosmoTheme.colors.danger),
            border = BorderStroke(States.selectionBorder, KosmoTheme.colors.danger),
            modifier = modifier,
        ) { Text(stringResource(R.string.action_stop), style = KosmoTheme.typography.button) }
    } else {
        ActionButton(R.string.action_start, state.startStopEnabled, primary = true, modifier, actions.onStart)
    }
}

/** Two options in one group: the current quality is spoken as the group's description (Q-quality, rule CP2). */
@Composable
private fun QualityToggle(state: CaptureUiState, onQuality: (VideoQuality) -> Unit, modifier: Modifier) {
    val current = stringResource(qualityLabel(state.quality))
    val description = stringResource(R.string.quality_description, current)
    Row(
        modifier = modifier.selectableGroup().semantics { contentDescription = description }
            .testTag(CaptureTags.QUALITY),
        horizontalArrangement = Arrangement.Center,
    ) {
        QualityOption(VideoQuality.UHD, CaptureTags.QUALITY_UHD, state, onQuality)
        QualityOption(VideoQuality.FHD, CaptureTags.QUALITY_FHD, state, onQuality)
    }
}

@Composable
private fun QualityOption(quality: VideoQuality, tag: String, state: CaptureUiState, onQuality: (VideoQuality) -> Unit) {
    val selected = state.quality == quality
    val colors = KosmoTheme.colors
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = States.minTouchTarget, minHeight = CONTROL_HEIGHT)
            .background(if (selected) colors.accent else colors.surface, KosmoShapes.control)
            .border(
                width = if (selected) States.selectionBorder else 1.dp,
                color = if (selected) colors.accent else colors.outline,
                shape = KosmoShapes.control,
            )
            .selectable(selected = selected, enabled = state.qualityEnabled, role = Role.RadioButton) {
                onQuality(quality)
            }
            .padding(horizontal = SPACING)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(qualityLabel(quality)),
            style = KosmoTheme.typography.button,
            color = if (selected) colors.onAccent else colors.onBackground,
            textAlign = TextAlign.Center,
            softWrap = false,
        )
    }
}

private fun qualityLabel(quality: VideoQuality): Int =
    if (quality == VideoQuality.UHD) R.string.quality_uhd else R.string.quality_fhd

@Composable
private fun KeypadColumn(state: CaptureUiState, actions: CaptureActions, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(KEY_GAP),
    ) {
        VolleyReadout(state)
        Keypad(
            isEnabled = state.keypadEnabled,
            onDart = actions.onDart,
            keyHeight = CONTROL_HEIGHT,
            gap = KEY_GAP,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SPACING)) {
            ActionButton(R.string.action_undo, state.undoEnabled, false, Modifier.weight(1f).testTag(CaptureTags.UNDO), actions.onUndo)
            ActionButton(
                R.string.action_next_volley,
                state.nextVolleyEnabled,
                true,
                Modifier.weight(1f).testTag(CaptureTags.NEXT_VOLLEY),
                actions.onNextVolley,
            )
        }
    }
}

/** A filled button when it is the enabled primary action, an outlined one otherwise (disabled has no fill). */
@Composable
private fun ActionButton(label: Int, enabled: Boolean, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val sized = modifier.controlSize()
    val text: @Composable RowScope.() -> Unit = { Text(stringResource(label), style = KosmoTheme.typography.button) }
    if (primary && enabled) {
        Button(onClick = onClick, contentPadding = BUTTON_PADDING, modifier = sized, content = text)
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, contentPadding = BUTTON_PADDING, modifier = sized, content = text)
    }
}

private fun Modifier.controlSize(): Modifier =
    defaultMinSize(minWidth = States.minTouchTarget, minHeight = CONTROL_HEIGHT)

private val BUTTON_PADDING = PaddingValues(horizontal = 12.dp, vertical = 4.dp)

/** Rule CP3: "Volley N", the three slots, and the full-volley hint, all from [CaptureUiState]. */
@Composable
private fun VolleyReadout(state: CaptureUiState) {
    val dash = stringResource(R.string.readout_empty_slot)
    val names = state.slots.map { it?.let { label -> labelText(label) } }
    val none = stringResource(R.string.readout_no_dart)
    val hint = stringResource(R.string.volley_full_hint)
    val spoken = names.filterNotNull().ifEmpty { listOf(none) }.joinToString(", ")
    val base = stringResource(R.string.readout_description, state.volleyNumber, spoken)
    val description = if (state.volleyFull) "$base. $hint" else base
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = READOUT_HEIGHT)
            .testTag(CaptureTags.READOUT).clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SPACING)) {
            Text(
                text = stringResource(R.string.readout_volley, state.volleyNumber),
                style = KosmoTheme.typography.labelLarge,
                color = KosmoTheme.colors.onBackground,
            )
            names.forEachIndexed { index, name -> DartSlot(name ?: dash, index) }
        }
        if (state.volleyFull) {
            Text(
                text = hint,
                style = KosmoTheme.typography.body,
                color = KosmoTheme.colors.accent,
                modifier = Modifier.testTag(CaptureTags.VOLLEY_FULL_HINT),
            )
        }
    }
}

private val READOUT_HEIGHT = 56.dp

@Composable
private fun DartSlot(text: String, index: Int) {
    Box(
        modifier = Modifier.defaultMinSize(minWidth = States.minTouchTarget, minHeight = 36.dp)
            .border(1.dp, KosmoTheme.colors.outline, KosmoShapes.control)
            .padding(horizontal = SPACING),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = KosmoTheme.typography.dart,
            color = KosmoTheme.colors.onBackground,
            modifier = Modifier.testTag(CaptureTags.slot(index)),
        )
    }
}

@Composable
private fun labelText(label: DartLabel): String = when (label) {
    is DartLabel.Text -> label.value
    DartLabel.Bull -> stringResource(R.string.readout_bull)
    DartLabel.Miss -> stringResource(R.string.readout_miss)
}
