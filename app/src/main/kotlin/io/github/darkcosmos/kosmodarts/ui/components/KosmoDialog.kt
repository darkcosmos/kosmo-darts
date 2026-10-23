package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme

/**
 * The four remaining confirmations (abandon, new game, no voice, restore failed; owner Q4 of issue #49), restyled: on
 * `surface`, radius 8, the confirm button on `accent`, the other one on `surfaceHigh`. Content and behavior unchanged.
 */
@Composable
fun KosmoDialog(
    onDismissRequest: () -> Unit,
    text: String,
    confirm: DialogAction,
    modifier: Modifier = Modifier,
    title: String? = null,
    dismiss: DialogAction? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val colors = KosmoTheme.colors
    AlertDialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
        modifier = modifier,
        shape = KosmoShapes.card,
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackgroundMuted,
        title = title?.let { { Text(text = it, style = KosmoTheme.typography.labelLarge) } },
        text = { Text(text = text, style = KosmoTheme.typography.body) },
        confirmButton = { KosmoButton(confirm.text, confirm.onClick, kind = ButtonKind.PRIMARY) },
        dismissButton = dismiss?.let { { KosmoButton(it.text, it.onClick, kind = ButtonKind.SECONDARY) } },
    )
}
