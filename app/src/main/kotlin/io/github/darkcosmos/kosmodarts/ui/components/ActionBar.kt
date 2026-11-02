package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Two buttons side by side (design system, `ActionBar`): the secondary one at the start (`surfaceHigh`), the main one
 * at the end (`accent`). Both share the taller height when a label wraps at a large font size (U6).
 */
@Composable
fun ActionBar(secondary: BarAction, primary: BarAction, minHeight: Dp, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.height(IntrinsicSize.Min),
    ) {
        listOf(secondary to ButtonKind.SECONDARY, primary to ButtonKind.PRIMARY).forEach { (action, kind) ->
            KosmoButton(
                text = action.text,
                onClick = action.onClick,
                kind = kind,
                enabled = action.isEnabled,
                minHeight = minHeight,
                description = action.description,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(action.tag),
            )
        }
    }
}
