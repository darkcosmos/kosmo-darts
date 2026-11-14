package io.github.darkcosmos.kosmodarts.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme

private const val MIN_POINTS = 2
private val CURVE_HEIGHT = 96.dp
private val CURVE_PADDING = 12.dp
private val LINE_WIDTH = 2.dp
private val DOT_RADIUS = 4.dp

/**
 * PS5: the curve of [display], drawn with Canvas on `surface`, scaled between the lowest and highest values of the
 * engine summary (a flat line in the middle when they are equal). The values and dates are shown as text by the block;
 * the drawing has the TalkBack description. Nothing is drawn with fewer than 2 points.
 */
@Composable
internal fun TrendCurve(display: TrendDisplay, modifier: Modifier = Modifier) {
    val low = display.summary.lowest
    val high = display.summary.highest
    if (display.points.size < MIN_POINTS || low == null || high == null) return
    val colors = KosmoTheme.colors
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(CURVE_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .padding(CURVE_PADDING)
            .semantics { contentDescription = display.description }
            .testTag(ProfileTags.TREND_CURVE),
    ) {
        val range = high - low
        val last = display.points.lastIndex
        fun at(index: Int): Offset {
            val ratio = if (range > 0.0) ((display.points[index].value - low) / range).toFloat() else HALF
            return Offset(size.width * index / last, size.height * (1f - ratio))
        }
        val path = Path().apply {
            moveTo(at(0).x, at(0).y)
            for (i in 1..last) at(i).let { lineTo(it.x, it.y) }
        }
        drawPath(
            path,
            colors.accent,
            style = Stroke(LINE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(colors.accent, DOT_RADIUS.toPx(), at(last))
    }
}

private const val HALF = 0.5f
