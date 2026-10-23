package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States

private val TRACK_WIDTH = 56.dp
private val TRACK_HEIGHT = 32.dp
private val THUMB = 24.dp
private val ROW_MIN_HEIGHT = 64.dp

/**
 * A setting that is on or off (design system, `SettingsSwitch`; AN13): the whole row toggles and is one TalkBack
 * switch with its label and [supportingText]. The switch is rectangular: on = `accent` track with a `background`
 * thumb at the end; off = `surfaceHigh` track with an `outline` border and a `onBackgroundMuted` thumb at the start
 * (the position tells the state, not only the color). A disabled row keeps its value, is drawn at 40 % and ignores
 * taps.
 */
@Composable
fun SettingsSwitch(
    label: String,
    checked: Boolean,
    tag: String,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
    below: (@Composable () -> Unit)? = null,
) {
    val colors = KosmoTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(KosmoShapes.control)
            .background(colors.surface),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_MIN_HEIGHT)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onToggle)
                .testTag(tag)
                .alpha(if (enabled) 1f else States.DISABLED_ALPHA)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = label, style = KosmoTheme.typography.bodyLarge, color = colors.onBackground)
                supportingText?.let {
                    Text(text = it, style = KosmoTheme.typography.body, color = colors.onBackgroundMuted)
                }
            }
            SwitchTrack(checked)
        }
        below?.let { Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) { it() } }
    }
}

@Composable
private fun SwitchTrack(checked: Boolean) {
    val colors = KosmoTheme.colors
    Box(
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier
            .size(TRACK_WIDTH, TRACK_HEIGHT)
            .clip(KosmoShapes.control)
            .background(if (checked) colors.accent else colors.surfaceHigh)
            .border(1.dp, if (checked) Color.Transparent else colors.outline, KosmoShapes.control)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .size(THUMB)
                .clip(KosmoShapes.control)
                .background(if (checked) colors.background else colors.onBackgroundMuted),
        )
    }
}
