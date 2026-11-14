package io.github.darkcosmos.kosmodarts.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.engine.board.BoardGeometry
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Smallest side of the board (HM8): 200 dp, so that the double ring stays readable in landscape. */
private val MIN_BOARD_SIZE = 200.dp
private val WIRE_WIDTH = 1.dp
private val SELECTION_WIDTH = 3.dp
private const val NUMBER_RADIUS_MM = 188.0
private const val NUMBER_HEIGHT_MM = 16.0
private const val SINGLE_INNER_END_MM = BoardGeometry.TRIPLE_INNER_MM
private const val SINGLE_OUTER_START_MM = BoardGeometry.TRIPLE_OUTER_MM
private const val TOP_DEGREES = -90.0

/**
 * HM8: a dartboard drawn with Canvas, with no rule logic. [colors] gives the colour of each zone (the mapping from
 * values to colours is the caller's), [neutral] the colour of a zone without an entry, [selected] is outlined in
 * `accent`. A tap is converted from pixels to millimetres and resolved by [BoardGeometry.zoneAt] (BC5); the tapped
 * zone, or nothing outside the double ring, is reported through [onZoneTapped]. A tap is a supplementary access
 * (some zones are smaller than 48 dp): the same information is given as text by the caller (PS8, no popup).
 *
 * TalkBack: the board has [contentDescription] and one custom action per zone, labelled by [zoneDescription]
 * (name, count and percentage, computed by the caller from its state) which reports that zone like a tap.
 *
 * The board is a square of at least 200 dp. The numbers around the ring scale with the board, not with the font
 * size, so that 200 % font does not move or clip them.
 */
@Composable
fun Dartboard(
    colors: Map<BoardZone, Color>,
    neutral: Color,
    selected: BoardZone?,
    onZoneTapped: (BoardZone) -> Unit,
    contentDescription: String,
    zoneDescription: (BoardZone) -> String,
    modifier: Modifier = Modifier,
) {
    val theme = KosmoTheme.colors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val actions = remember(zoneDescription, onZoneTapped) {
        BoardZone.ALL.map { zone ->
            CustomAccessibilityAction(zoneDescription(zone)) {
                onZoneTapped(zone)
                true
            }
        }
    }
    Canvas(
        modifier = modifier
            .sizeIn(minWidth = MIN_BOARD_SIZE, minHeight = MIN_BOARD_SIZE)
            .aspectRatio(1f)
            .testTag(DartboardTags.BOARD)
            .semantics {
                this.contentDescription = contentDescription
                customActions = actions
            }
            .pointerInput(onZoneTapped) {
                detectTapGestures { offset ->
                    zoneAtPixel(offset, min(size.width, size.height).toFloat())?.let(onZoneTapped)
                }
            },
    ) {
        val pxPerMm = (min(size.width, size.height) / 2f) / HALF_SIDE_MM.toFloat()
        val centre = Offset(size.width / 2f, size.height / 2f)
        val wire = WIRE_WIDTH.toPx()
        BoardZone.ALL.forEach { zone ->
            val fill = colors[zone] ?: neutral
            zonePaths(zone, centre, pxPerMm).forEach { path ->
                drawPath(path, fill)
                drawPath(path, theme.background, style = Stroke(wire))
            }
        }
        val numberSize = with(density) { (NUMBER_HEIGHT_MM * pxPerMm).toFloat().toSp() }
        drawNumbers(measurer, theme.onBackground, centre, pxPerMm, numberSize)
        selected?.let { zone ->
            zonePaths(zone, centre, pxPerMm).forEach { path ->
                drawPath(path, theme.accent, style = Stroke(SELECTION_WIDTH.toPx()))
            }
        }
    }
}

private fun DrawScope.drawNumbers(
    measurer: TextMeasurer,
    color: Color,
    centre: Offset,
    pxPerMm: Float,
    fontSize: TextUnit,
) {
    val style = TextStyle(color = color, fontSize = fontSize, fontWeight = FontWeight.Bold)
    BoardGeometry.SECTOR_ORDER.forEachIndexed { index, number ->
        val angle = Math.toRadians(TOP_DEGREES + index * BoardGeometry.SECTOR_ANGLE_DEGREES)
        val radius = (NUMBER_RADIUS_MM * pxPerMm).toFloat()
        val layout = measurer.measure(number.toString(), style)
        drawText(
            layout,
            topLeft = Offset(
                centre.x + radius * cos(angle).toFloat() - layout.size.width / 2f,
                centre.y + radius * sin(angle).toFloat() - layout.size.height / 2f,
            ),
        )
    }
}

/** The shapes of [zone] on a board centred on [centre]: two bands for a single, one otherwise. */
private fun zonePaths(zone: BoardZone, centre: Offset, pxPerMm: Float): List<Path> = when (zone.ring) {
    BoardZone.Ring.BULL -> listOf(disc(centre, BoardGeometry.BULL_RADIUS_MM, pxPerMm))
    BoardZone.Ring.OUTER_BULL -> listOf(disc(centre, BoardGeometry.OUTER_BULL_RADIUS_MM, pxPerMm))
    BoardZone.Ring.TRIPLE ->
        listOf(sector(zone.number, BoardGeometry.TRIPLE_INNER_MM, BoardGeometry.TRIPLE_OUTER_MM, centre, pxPerMm))
    BoardZone.Ring.DOUBLE ->
        listOf(sector(zone.number, BoardGeometry.DOUBLE_INNER_MM, BoardGeometry.DOUBLE_OUTER_MM, centre, pxPerMm))
    BoardZone.Ring.SINGLE -> listOf(
        sector(zone.number, BoardGeometry.OUTER_BULL_RADIUS_MM, SINGLE_INNER_END_MM, centre, pxPerMm),
        sector(zone.number, SINGLE_OUTER_START_MM, BoardGeometry.DOUBLE_INNER_MM, centre, pxPerMm),
    )
}

private fun disc(centre: Offset, radiusMm: Double, pxPerMm: Float): Path =
    Path().apply { addOval(circle(centre, radiusMm, pxPerMm)) }

private fun circle(centre: Offset, radiusMm: Double, pxPerMm: Float): Rect =
    Rect(centre, (radiusMm * pxPerMm).toFloat())

private fun sector(number: Int, innerMm: Double, outerMm: Double, centre: Offset, pxPerMm: Float): Path {
    val index = BoardGeometry.SECTOR_ORDER.indexOf(number)
    val start = (TOP_DEGREES + index * BoardGeometry.SECTOR_ANGLE_DEGREES - BoardGeometry.SECTOR_ANGLE_DEGREES / 2)
        .toFloat()
    val sweep = BoardGeometry.SECTOR_ANGLE_DEGREES.toFloat()
    return Path().apply {
        arcTo(circle(centre, outerMm, pxPerMm), start, sweep, true)
        arcTo(circle(centre, innerMm, pxPerMm), start + sweep, -sweep, false)
        close()
    }
}
