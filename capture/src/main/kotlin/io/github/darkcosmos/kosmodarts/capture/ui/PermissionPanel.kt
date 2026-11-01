package io.github.darkcosmos.kosmodarts.capture.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.capture.R
import io.github.darkcosmos.kosmodarts.capture.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.capture.ui.theme.States

/** Shown instead of the preview while the camera permission is missing; after a permanent refusal it opens the settings. */
@Composable
fun PermissionPanel(permission: CameraPermission, onAllow: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val permanent = permission == CameraPermission.PERMANENTLY_DENIED
    Column(
        modifier = modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()).testTag(CaptureTags.PERMISSION_PANEL),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.permission_message),
            style = KosmoTheme.typography.bodyLarge,
            color = KosmoTheme.colors.onBackground,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = if (permanent) onOpenSettings else onAllow,
            modifier = Modifier.defaultMinSize(minWidth = States.minTouchTarget, minHeight = CONTROL_HEIGHT).testTag(CaptureTags.PERMISSION_BUTTON),
        ) {
            Text(
                text = stringResource(if (permanent) R.string.permission_open_settings else R.string.permission_allow),
                style = KosmoTheme.typography.button,
            )
        }
    }
}
