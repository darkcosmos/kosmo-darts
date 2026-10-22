package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.game.VisitBoxText
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme

private val BOX_PADDING = 8.dp

// Absorbs the rounding of the measured text width to whole pixels, so the box text never needs a second line.
private val BOX_SLACK = 1.dp

// VT11: a box fits BUST and this number, the highest visit total.
private const val BOX_NUMBER_REFERENCE = "180"

/**
 * The visit total of a player (docs/rules/visit-total.md, VT8 to VT11; design system, `VisitBox`): no label, a
 * filled box (white, text `background`) for the current visit in the zone, an outlined box (border
 * `onBackgroundMuted`) on the tiles. Its width fits the widest of BUST and 180 in [style], so it never changes with
 * the value. TalkBack reads its description instead of the bare number.
 */
@Composable
fun VisitBox(
    text: VisitBoxText,
    index: Int,
    isFilled: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val colors = KosmoTheme.colors
    val args = text.formatArgs.toTypedArray()
    val value = stringResource(text.valueRes, *args)
    val description = stringResource(text.descriptionRes, *args)
    val bust = stringResource(R.string.game_bust)
    val width = maxOf(measuredWidth(bust, style), measuredWidth(BOX_NUMBER_REFERENCE, style))
    Surface(
        color = if (isFilled) colors.onBackground else Color.Transparent,
        contentColor = if (isFilled) colors.background else colors.onBackground,
        border = if (isFilled) null else BorderStroke(1.dp, colors.onBackgroundMuted),
        shape = KosmoShapes.control,
        modifier = modifier
            .width(width + BOX_PADDING * 2 + BOX_SLACK)
            .testTag(GameTags.visitTotal(index))
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = value,
                style = style,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    // The filled box of the zone has the fixed height of the dart slots: no vertical padding there.
                    .padding(horizontal = BOX_PADDING, vertical = if (isFilled) 0.dp else BOX_PADDING / 2),
            )
        }
    }
}

/** Width of [text] laid out on one line in [style], at the current density and font scale. Render-only. */
@Composable
fun measuredWidth(text: String, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, style, density, measurer) {
        with(density) { measurer.measure(text, style, softWrap = false, maxLines = 1).size.width.toDp() }
    }
}
