package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme

/**
 * The top line of the game screen (design system, `MatchHeader`): the mode and the finish ("501 · DOUBLE OUT") at the
 * start, the position in the match ("SET 2 · LEG 3", M14, M19) at the end, nothing there in a single game. Both in
 * `label`, `onBackgroundMuted`. When both do not fit on one line (a narrow screen at a large font size), the position
 * goes to a second line, at the end, instead of squeezing the mode onto three lines (U12).
 */
@Composable
fun MatchHeader(mode: String, position: String?, modifier: Modifier = Modifier) {
    val style = KosmoTheme.typography.label
    val color = KosmoTheme.colors.onBackgroundMuted
    Layout(
        modifier = modifier,
        content = {
            Text(text = mode, style = style, color = color, modifier = Modifier.testTag(GameTags.HEADER_MODE))
            position?.let {
                Text(text = it, style = style, color = color, modifier = Modifier.testTag(GameTags.HEADER_POSITION))
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = HEADER_GAP.roundToPx()
        val loose = Constraints(maxWidth = width)
        val modeMeasurable = measurables.first()
        val positionMeasurable = measurables.getOrNull(1)
        val sideBySide = positionMeasurable == null || modeMeasurable.maxIntrinsicWidth(Int.MAX_VALUE) + gap +
            positionMeasurable.maxIntrinsicWidth(Int.MAX_VALUE) <= width
        val positionPlaceable = positionMeasurable?.measure(loose)
        val modeWidth = if (sideBySide && positionPlaceable != null) width - positionPlaceable.width - gap else width
        val modePlaceable = modeMeasurable.measure(Constraints(maxWidth = maxOf(0, modeWidth)))
        val positionHeight = positionPlaceable?.height ?: 0
        val stackedHeight = modePlaceable.height + positionHeight
        val height = if (sideBySide) maxOf(modePlaceable.height, positionHeight) else stackedHeight
        layout(width, height) {
            modePlaceable.placeRelative(0, if (sideBySide) (height - modePlaceable.height) / 2 else 0)
            positionPlaceable?.placeRelative(
                x = width - positionPlaceable.width,
                y = if (sideBySide) (height - positionHeight) / 2 else modePlaceable.height,
            )
        }
    }
}

private val HEADER_GAP = 12.dp
