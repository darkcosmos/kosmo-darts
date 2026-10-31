package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.fixedSp
import kotlin.math.max

/**
 * What the board header measured with the real texts (CR22): the size picked for the totals, and the header height
 * that goes into [CricketBoardLayoutPolicy]. [totalSize] and [totalLineDp] are `null` in a solo game (no total).
 */
class CricketHeaderMetrics(val totalSize: TotalSize?, val totalLineDp: Float?, val heightDp: Float)

/**
 * Measures the board header for the given [totals] and [playerColumnWidthDp]: the largest of [totalSizesSp] whose
 * digits fit the column (the em is read from [Density], which applies Android's non-linear font scaling), then the line
 * heights of the total and of the name, as Compose draws them. The header is never under the 48 dp of the "Miss" cell.
 */
@Composable
internal fun rememberHeaderMetrics(
    totals: List<Int>?,
    playerColumnWidthDp: Float,
    totalSizesSp: List<Float>,
): CricketHeaderMetrics {
    val density = LocalDensity.current
    val typography = KosmoTheme.typography
    val measurer = rememberTextMeasurer()
    val nameLine = lineHeightDp(measurer, density, typography.label)
    val size = totals?.let {
        val digits = it.maxOf { total -> total.toString().length }
        val candidates = totalSizesSp.map { sp -> TotalSizeCandidate(sp, with(density) { sp.sp.toDp().value }) }
        CricketBoardLayoutPolicy.totalTextSize(digits, playerColumnWidthDp, candidates)
    }
    val totalLine = size?.let { lineHeightDp(measurer, density, totalStyle(typography.tileScore, it, density)) }
    val height = CricketBoardLayoutPolicy.columnHeaderHeightDp(totalLine, nameLine)
    return CricketHeaderMetrics(size, totalLine, max(height, CricketBoardLayoutPolicy.ROW_HEIGHT_MIN))
}

/** The style of a total: the display font at the picked size, fixed (not following the font scale) as a fallback. */
internal fun totalStyle(base: TextStyle, size: TotalSize, density: Density): TextStyle =
    base.copy(fontSize = if (size.isFixed) density.fixedSp(size.sizeSp) else size.sizeSp.sp)

/** The height in dp of one line of [style], measured with the real font (the policy does no font arithmetic). */
@Composable
internal fun rememberLineHeightDp(style: TextStyle): Float =
    lineHeightDp(rememberTextMeasurer(), LocalDensity.current, style)

private fun lineHeightDp(measurer: TextMeasurer, density: Density, style: TextStyle): Float =
    with(density) { measurer.measure("0", style).size.height.toDp().value }
