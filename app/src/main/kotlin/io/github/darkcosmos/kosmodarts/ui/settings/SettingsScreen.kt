package io.github.darkcosmos.kosmodarts.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.audio.VoiceStatus
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme

private val CONTENT_MAX_WIDTH = 560.dp
private val MIN_TOUCH_TARGET = 48.dp
private val SWITCH_ROW_MIN_HEIGHT = 56.dp

/**
 * The "Settings" screen (AN13): Voice and Sounds switches, the voice status with "Install a voice" when needed, and
 * the sound credits at the bottom. Back (arrow or system) returns to the home screen.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth()) {
                TopBar(onBack)
                Spacer(modifier = Modifier.height(16.dp))
                SwitchRow(
                    label = stringResource(R.string.settings_voice),
                    checked = state.voiceEnabled,
                    tag = SettingsTags.VOICE_SWITCH,
                    onToggle = { onEvent(SettingsEvent.VoiceToggled(it)) },
                )
                VoiceStatusLine(state, onInstall = { onEvent(SettingsEvent.InstallVoiceTapped) })
                Spacer(modifier = Modifier.height(16.dp))
                SwitchRow(
                    label = stringResource(R.string.settings_sounds),
                    checked = state.soundsEnabled,
                    tag = SettingsTags.SOUNDS_SWITCH,
                    onToggle = { onEvent(SettingsEvent.SoundsToggled(it)) },
                )
                Spacer(modifier = Modifier.height(40.dp))
                Text(
                    text = stringResource(R.string.settings_sound_credits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .testTag(SettingsTags.CREDITS),
                )
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
                .testTag(SettingsTags.BACK),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.settings_back),
            )
        }
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(start = 8.dp)
                .semantics { heading() },
        )
    }
}

/** The whole row toggles; TalkBack reads it as one switch with its label. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, tag: String, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = SWITCH_ROW_MIN_HEIGHT)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
            .testTag(tag)
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** AN13, AN17: the reason the voice is or is not available. A live region, so TalkBack reads changes (U1, U2). */
@Composable
private fun VoiceStatusLine(state: SettingsUiState, onInstall: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Text(
            text = stringResource(statusText(state.voiceStatus)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .testTag(SettingsTags.VOICE_STATUS)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (state.canInstall) {
            OutlinedButton(
                onClick = onInstall,
                modifier = Modifier
                    .sizeIn(minHeight = MIN_TOUCH_TARGET)
                    .testTag(SettingsTags.INSTALL_VOICE),
            ) {
                Text(stringResource(R.string.voice_install))
            }
        }
    }
}

private fun statusText(status: VoiceStatus): Int = when (status) {
    VoiceStatus.CHECKING -> R.string.settings_voice_checking
    VoiceStatus.AVAILABLE -> R.string.settings_voice_available
    VoiceStatus.NO_USABLE_VOICE -> R.string.settings_voice_missing
    VoiceStatus.NO_ENGINE -> R.string.settings_voice_no_engine
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    KosmoDartsTheme {
        SettingsScreen(
            state = SettingsUiState(
                voiceEnabled = true,
                soundsEnabled = true,
                voiceStatus = VoiceStatus.NO_USABLE_VOICE,
                canInstall = true,
            ),
            onEvent = {},
            onBack = {},
        )
    }
}
