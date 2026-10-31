package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import io.github.darkcosmos.kosmodarts.ui.components.WordSafeText

/** The smallest size of the vertical label (R2.2, Q17 B): the 28 sp key size shrinks to here at most. */
private val VERTICAL_LABEL_MIN_FONT = 20.sp

/**
 * The label of a tall key, turned a quarter-turn counter-clockwise so it reads from bottom to top (R2.2, Q17 B). The
 * text is laid out with the parent's height as its line length and the parent's width as its height, with the
 * word-safe rules of [WordSafeText] (the style's size, down to 20 sp, breaks only between words). Its semantics text
 * is the label itself, so TalkBack is unchanged. Needs bounded constraints.
 */
@Composable
internal fun VerticalKeyLabel(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .layout { measurable, constraints ->
                val swapped = Constraints.fixed(constraints.maxHeight, constraints.maxWidth)
                val placeable = measurable.measure(swapped)
                layout(placeable.height, placeable.width) {
                    // The turned box shares the center of the unturned one.
                    placeable.place((placeable.height - placeable.width) / 2, (placeable.width - placeable.height) / 2)
                }
            }
            .graphicsLayer { rotationZ = -QUARTER_TURN }
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        WordSafeText(text = text, style = style, minFontSize = VERTICAL_LABEL_MIN_FONT)
    }
}

private const val QUARTER_TURN = 90f
