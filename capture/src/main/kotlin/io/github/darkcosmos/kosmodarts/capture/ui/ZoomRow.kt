package io.github.darkcosmos.kosmodarts.capture.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.capture.R
import io.github.darkcosmos.kosmodarts.capture.ui.keypad.WordSafeText
import io.github.darkcosmos.kosmodarts.capture.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.capture.ui.theme.States

private val VALUE_MIN_WIDTH = 64.dp
private val VALUE_MAX_WIDTH = 130.dp
private val ROW_GAP = 4.dp
private const val VALUE_MAX_LINES = 2

/**
 * The zoom row (rules CP14, CP15): value, "-" button, slider, "+" button. It only renders [state] and forwards events;
 * the slider position comes from the zoom selection and every ratio computation stays in the view model.
 */
@Composable
internal fun ZoomRow(state: CaptureUiState, actions: CaptureActions, modifier: Modifier = Modifier) {
    val zoom = state.zoom
    val unavailable = zoom.unavailable
    val valueText = if (unavailable) stringResource(R.string.zoom_unavailable) else stringResource(R.string.zoom_value, zoom.ratio)
    val spoken = if (unavailable) valueText else stringResource(R.string.zoom_state, zoom.ratio)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides CONTROL_HEIGHT) {
        Row(
            modifier = modifier.fillMaxWidth().height(CONTROL_HEIGHT).testTag(CaptureTags.ZOOM_ROW),
            horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZoomValue(valueText, state.zoomEnabled)
            ZoomButton(R.string.zoom_out_symbol, R.string.zoom_out_description, state.zoomOutEnabled, CaptureTags.ZOOM_OUT) {
                actions.onZoomStep(-1)
            }
            ZoomSlider(state, actions, spoken, Modifier.weight(1f))
            ZoomButton(R.string.zoom_in_symbol, R.string.zoom_in_description, state.zoomInEnabled, CaptureTags.ZOOM_IN) {
                actions.onZoomStep(1)
            }
        }
    }
}

/** Visible value; cleared from semantics so that the zoom is read once, by the slider. */
@Composable
private fun ZoomValue(text: String, enabled: Boolean) {
    val color = KosmoTheme.colors.onBackground
    Box(
        modifier = Modifier.widthIn(min = VALUE_MIN_WIDTH, max = VALUE_MAX_WIDTH).height(CONTROL_HEIGHT)
            .testTag(CaptureTags.ZOOM_VALUE).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        WordSafeText(
            text = text,
            style = KosmoTheme.typography.button.copy(color = if (enabled) color else color.copy(alpha = States.DISABLED_ALPHA)),
            maxLines = VALUE_MAX_LINES,
        )
    }
}

/** Continuous slider (no steps); a polite live region so a button press is announced without moving the focus (DZ17). */
@Composable
private fun ZoomSlider(state: CaptureUiState, actions: CaptureActions, spoken: String, modifier: Modifier) {
    val description = stringResource(R.string.zoom_description)
    val colors = KosmoTheme.colors
    val dim = { color: Color -> color.copy(alpha = States.DISABLED_ALPHA) }
    Slider(
        value = state.zoom.position,
        onValueChange = actions.onZoomChanged,
        onValueChangeFinished = actions.onZoomChangeFinished,
        enabled = state.zoomEnabled,
        colors = SliderDefaults.colors(
            thumbColor = colors.accent,
            activeTrackColor = colors.accent,
            inactiveTrackColor = colors.surfaceHigher,
            disabledThumbColor = dim(colors.accent),
            disabledActiveTrackColor = dim(colors.accent),
            disabledInactiveTrackColor = dim(colors.surfaceHigher),
        ),
        modifier = modifier.height(CONTROL_HEIGHT).semantics {
            contentDescription = description
            stateDescription = spoken
            liveRegion = LiveRegionMode.Polite
        }.testTag(CaptureTags.ZOOM_SLIDER),
    )
}

/**
 * Enabled: filled with `surfaceHigher` and an accent outline. Disabled (DZ16): no fill, outline and glyph at
 * [States.DISABLED_ALPHA], so the end of the range shows in the shape and not by opacity alone.
 */
@Composable
private fun ZoomButton(symbol: Int, description: Int, enabled: Boolean, tag: String, onClick: () -> Unit) {
    val colors = KosmoTheme.colors
    val label = stringResource(description)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.surfaceHigher,
            contentColor = colors.onBackground,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.onBackground.copy(alpha = States.DISABLED_ALPHA),
        ),
        border = BorderStroke(1.dp, if (enabled) colors.accent else colors.accent.copy(alpha = States.DISABLED_ALPHA)),
        modifier = Modifier.size(ZOOM_BUTTON_WIDTH, ZOOM_BUTTON_HEIGHT).semantics { contentDescription = label }
            .testTag(tag),
    ) { Text(stringResource(symbol), style = KosmoTheme.typography.button) }
}
