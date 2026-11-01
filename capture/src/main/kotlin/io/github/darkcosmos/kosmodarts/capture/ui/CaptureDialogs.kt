package io.github.darkcosmos.kosmodarts.capture.ui

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.capture.R
import io.github.darkcosmos.kosmodarts.capture.ui.theme.States

/** Rule CP6: Stop asks first; the current volley is saved either way. */
@Composable
fun StopConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    ConfirmDialog(
        message = R.string.stop_message,
        confirm = R.string.stop_confirm,
        cancel = R.string.stop_cancel,
        tags = DialogTags(CaptureTags.STOP_DIALOG, CaptureTags.STOP_CONFIRM, CaptureTags.STOP_CANCEL),
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}

/** Rule CP8: Delete all names what is removed and what stays (the reinstall note). */
@Composable
fun DeleteAllDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    ConfirmDialog(
        message = R.string.delete_all_message,
        confirm = R.string.delete_all_confirm,
        cancel = R.string.dialog_cancel,
        tags = DialogTags(CaptureTags.DELETE_DIALOG, CaptureTags.DELETE_CONFIRM, CaptureTags.DELETE_CANCEL),
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}

private data class DialogTags(val dialog: String, val confirm: String, val cancel: String)

@Composable
private fun ConfirmDialog(
    message: Int,
    confirm: Int,
    cancel: Int,
    tags: DialogTags,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        text = { Text(stringResource(message), modifier = Modifier.testTag(tags.dialog)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.dialogButton().testTag(tags.confirm)) {
                Text(stringResource(confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.dialogButton().testTag(tags.cancel)) {
                Text(stringResource(cancel))
            }
        },
    )
}

private fun Modifier.dialogButton(): Modifier =
    defaultMinSize(minWidth = States.minTouchTarget, minHeight = 48.dp)
