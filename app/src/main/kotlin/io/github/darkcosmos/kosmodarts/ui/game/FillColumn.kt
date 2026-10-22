package io.github.darkcosmos.kosmodarts.ui.game

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * A column of the game screen (plan of issue #49, section 4b): every child but [fill] keeps its natural (measured)
 * height, and [fill] (the zone, or the space between the keypad and the action bar) gets the rest of the visible
 * height, never less than [fillMinHeight] (the zone's fixed parts with the score at its floor, from the layout
 * policy). When the children do not fit, the column scrolls: the keypad and the action bar are never clipped.
 */
@Composable
internal fun FillColumn(
    gap: Dp,
    verticalPadding: Dp,
    fillMinHeight: Dp,
    modifier: Modifier = Modifier,
    before: List<@Composable () -> Unit>,
    fill: @Composable () -> Unit,
    after: List<@Composable () -> Unit>,
) {
    BoxWithConstraints(modifier = modifier) {
        val visibleHeight = constraints.maxHeight
        Layout(
            contents = listOf({ before.forEach { it() } }, fill, { after.forEach { it() } }),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) { (beforeMeasurables, fillMeasurables, afterMeasurables), constraints ->
            val width = constraints.maxWidth
            val loose = Constraints(maxWidth = width)
            val gapPx = gap.roundToPx()
            val padding = verticalPadding.roundToPx()
            val beforePlaceables = beforeMeasurables.map { it.measure(loose) }
            val afterPlaceables = afterMeasurables.map { it.measure(loose) }
            val children = beforePlaceables.size + afterPlaceables.size + 1
            val used = (beforePlaceables + afterPlaceables).sumOf { it.height } + gapPx * (children - 1) + 2 * padding
            val fillHeight = maxOf(fillMinHeight.roundToPx(), visibleHeight - used)
            val exact = Constraints(minWidth = width, maxWidth = width, minHeight = fillHeight, maxHeight = fillHeight)
            val fillPlaceables = fillMeasurables.map { it.measure(exact) }
            val height = maxOf(visibleHeight, used + fillHeight)
            layout(width, height) {
                var y = padding
                (beforePlaceables + fillPlaceables + afterPlaceables).forEach { placeable ->
                    placeable.placeRelative(0, y)
                    y += placeable.height + gapPx
                }
            }
        }
    }
}
