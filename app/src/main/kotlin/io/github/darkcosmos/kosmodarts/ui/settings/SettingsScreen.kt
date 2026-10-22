package io.github.darkcosmos.kosmodarts.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.audio.VoiceStatus
import io.github.darkcosmos.kosmodarts.ui.components.ButtonKind
import io.github.darkcosmos.kosmodarts.ui.components.KosmoButton
import io.github.darkcosmos.kosmodarts.ui.components.ScreenTopBar
import io.github.darkcosmos.kosmodarts.ui.components.SectionLabel
import io.github.darkcosmos.kosmodarts.ui.components.SettingsSwitch
import io.github.darkcosmos.kosmodarts.ui.layout.ScreenLayoutPolicy
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme

/**
 * The "Settings" screen (mockup 10; AN13): "SOUND AND VOICE" with the Voice switch (its status line and "Install a
 * voice" when needed), "Call each dart" and "Sounds", then "CREDITS": the sound credit and the Saira font credit
 * (owner Q7 of issue #49). Back (arrow or system) returns to the home screen.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KosmoTheme.colors
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .widthIn(max = ScreenLayoutPolicy.CONTENT_MAX_WIDTH.dp)
                .fillMaxWidth()
                .padding(
                    horizontal = ScreenLayoutPolicy.HORIZONTAL_MARGIN.dp,
                    vertical = ScreenLayoutPolicy.VERTICAL_MARGIN.dp,
                ),
        ) {
            ScreenTopBar(stringResource(R.string.settings_title), onBack = onBack, backTag = SettingsTags.BACK)
            SectionLabel(stringResource(R.string.settings_section_sound), Modifier.padding(top = 8.dp))
            SettingsSwitch(
                label = stringResource(R.string.settings_voice),
                checked = state.voiceEnabled,
                tag = SettingsTags.VOICE_SWITCH,
                onToggle = { onEvent(SettingsEvent.VoiceToggled(it)) },
                below = { VoiceStatusLine(state, onInstall = { onEvent(SettingsEvent.InstallVoiceTapped) }) },
            )
            SettingsSwitch(
                label = stringResource(R.string.settings_call_each_dart),
                checked = state.callEachDart,
                tag = SettingsTags.CALL_EACH_DART_SWITCH,
                onToggle = { onEvent(SettingsEvent.CallEachDartToggled(it)) },
                enabled = state.voiceEnabled,
                supportingText = if (state.voiceEnabled) {
                    null
                } else {
                    stringResource(R.string.settings_call_each_dart_needs_voice)
                },
            )
            SettingsSwitch(
                label = stringResource(R.string.settings_sounds),
                checked = state.soundsEnabled,
                tag = SettingsTags.SOUNDS_SWITCH,
                onToggle = { onEvent(SettingsEvent.SoundsToggled(it)) },
            )
            SectionLabel(stringResource(R.string.settings_credits), Modifier.padding(top = 16.dp))
            Credit(stringResource(R.string.settings_sound_credits), SettingsTags.CREDITS)
            Credit(stringResource(R.string.settings_font_credit), SettingsTags.FONT_CREDIT)
        }
    }
}

@Composable
private fun Credit(text: String, tag: String) {
    Text(
        text = text,
        style = KosmoTheme.typography.body,
        color = KosmoTheme.colors.onBackgroundMuted,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .testTag(tag),
    )
}

/** AN13, AN17: the reason the voice is or is not available. A live region, so TalkBack reads changes (U1, U2). */
@Composable
private fun VoiceStatusLine(state: SettingsUiState, onInstall: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(statusText(state.voiceStatus)),
            style = KosmoTheme.typography.body,
            color = KosmoTheme.colors.onBackgroundMuted,
            modifier = Modifier
                .testTag(SettingsTags.VOICE_STATUS)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (state.canInstall) {
            KosmoButton(
                text = stringResource(R.string.voice_install),
                onClick = onInstall,
                kind = ButtonKind.OUTLINED_ACCENT,
                modifier = Modifier.testTag(SettingsTags.INSTALL_VOICE),
            )
        }
    }
}

private fun statusText(status: VoiceStatus): Int = when (status) {
    VoiceStatus.CHECKING -> R.string.settings_voice_checking
    VoiceStatus.AVAILABLE -> R.string.settings_voice_available
    VoiceStatus.NO_USABLE_VOICE -> R.string.settings_voice_missing
    VoiceStatus.NO_ENGINE -> R.string.settings_voice_no_engine
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun SettingsScreenPreview() {
    KosmoDartsTheme {
        SettingsScreen(
            state = SettingsUiState(
                voiceEnabled = true,
                soundsEnabled = true,
                callEachDart = false,
                voiceStatus = VoiceStatus.NO_USABLE_VOICE,
                canInstall = true,
            ),
            onEvent = {},
            onBack = {},
        )
    }
}
