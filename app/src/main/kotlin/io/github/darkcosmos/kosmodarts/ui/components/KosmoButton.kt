package io.github.darkcosmos.kosmodarts.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    description: String? = null,
    @DrawableRes iconRes: Int? = null,
) {
    val colors = KosmoTheme.colors
    val (container, content) = when (kind) {
        ButtonKind.PRIMARY -> colors.accent to colors.onAccent
        ButtonKind.SECONDARY -> colors.surfaceHigh to colors.onBackground
        ButtonKind.OUTLINED_ACCENT -> Color.Transparent to colors.accent
        ButtonKind.DESTRUCTIVE -> colors.surfaceHigh to colors.dangerText
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
        modifier = modifier
            .heightIn(min = maxOf(minHeight, States.minTouchTarget))
            .semantics { description?.let { contentDescription = it } },
    ) {
        if (iconRes != null) {
            // Decorative (D13): the text is the accessible name, so TalkBack does not read the button twice.
            Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(ICON_SIZE))
            Spacer(Modifier.width(ICON_GAP))
        }
        WordSafeText(text = text, style = KosmoTheme.typography.button)
    }
}

private val ICON_SIZE = 20.dp
private val ICON_GAP = 8.dp
