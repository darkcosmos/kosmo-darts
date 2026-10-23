package io.github.darkcosmos.kosmodarts.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.NoVoiceNotice
import io.github.darkcosmos.kosmodarts.ui.components.DialogAction
import io.github.darkcosmos.kosmodarts.ui.components.KosmoDialog

/**
 * AN16: shown once per device. "OK" and back close it; a tap outside does not, so that a keypad tap landing as it
 * opens neither closes it unread nor enters a dart. Restyled with [KosmoDialog] (owner Q4 of issue #49).
 */
@Composable
internal fun NoVoiceDialog(notice: NoVoiceNotice, onEvent: (AppEvent) -> Unit) {
    KosmoDialog(
        onDismissRequest = { onEvent(AppEvent.NoVoiceNoticeDismissed) },
        properties = DialogProperties(dismissOnClickOutside = false),
        modifier = Modifier.testTag(GameTags.NO_VOICE_DIALOG),
        title = stringResource(R.string.no_voice_title),
        text = stringResource(R.string.no_voice_text),
        confirm = DialogAction(stringResource(R.string.no_voice_ok)) { onEvent(AppEvent.NoVoiceNoticeDismissed) },
        dismiss = if (notice.canInstall) {
            DialogAction(stringResource(R.string.voice_install)) { onEvent(AppEvent.InstallVoiceTapped) }
        } else {
            null
        },
    )
}
