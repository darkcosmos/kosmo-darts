package io.github.darkcosmos.kosmodarts.ui.game

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.NoVoiceNotice

/**
 * AN16: shown once per device. "OK" and back close it; a tap outside does not, so that a keypad tap landing as it
 * opens neither closes it unread nor enters a dart.
 */
@Composable
internal fun NoVoiceDialog(notice: NoVoiceNotice, onEvent: (AppEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(AppEvent.NoVoiceNoticeDismissed) },
        properties = DialogProperties(dismissOnClickOutside = false),
        modifier = Modifier.testTag(GameTags.NO_VOICE_DIALOG),
        title = { Text(stringResource(R.string.no_voice_title)) },
        text = { Text(stringResource(R.string.no_voice_text)) },
        confirmButton = {
            TextButton(onClick = { onEvent(AppEvent.NoVoiceNoticeDismissed) }) {
                Text(stringResource(R.string.no_voice_ok))
            }
        },
        dismissButton = if (notice.canInstall) {
            {
                TextButton(onClick = { onEvent(AppEvent.InstallVoiceTapped) }) {
                    Text(stringResource(R.string.voice_install))
                }
            }
        } else {
            null
        },
    )
}
