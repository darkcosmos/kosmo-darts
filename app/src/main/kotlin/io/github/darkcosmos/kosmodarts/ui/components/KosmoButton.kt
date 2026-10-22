package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States

/**
 * A button of the design system (`ActionBar`, screens, dialogs): rectangular (radius 4), label in the `button` style.
 * Its height is a minimum: a label that wraps on two lines at a large font size grows the button (U6), never clipped.
 */
@Composable
fun KosmoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    enabled: Boolean = true,
    minHeight: Dp = States.minTouchTarget,
) {
    val colors = KosmoTheme.colors
    val (container, content) = when (kind) {
        ButtonKind.PRIMARY -> colors.accent to colors.onAccent
        ButtonKind.SECONDARY -> colors.surfaceHigh to colors.onBackground
        ButtonKind.OUTLINED_ACCENT -> Color.Transparent to colors.accent
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = KosmoShapes.control,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = container.alpha * States.DISABLED_ALPHA),
            disabledContentColor = content.copy(alpha = States.DISABLED_ALPHA),
        ),
        border = if (kind == ButtonKind.OUTLINED_ACCENT) BorderStroke(1.dp, colors.accent) else null,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier.heightIn(min = maxOf(minHeight, States.minTouchTarget)),
    ) {
        Text(text = text, style = KosmoTheme.typography.button, textAlign = TextAlign.Center)
    }
}
