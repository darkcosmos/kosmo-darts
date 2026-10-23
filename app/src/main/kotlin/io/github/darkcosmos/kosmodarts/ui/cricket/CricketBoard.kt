package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketTarget
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVisit
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.ui.format.displayName
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.PlayerPalette

private val STRIKE = 2.dp
private val NAME_MIN_FONT = 10.sp
private val NAME_MAX_FONT = CricketLayoutPolicy.ROW_LABEL_TEXT_SIZE.sp
private val COLUMN_BORDER = 2.dp
private val MARK_SIZE = 18.dp
private val MARK_STROKE = 2.dp
private const val ROWS_TRAVERSAL_INDEX = 10f

/**
 * Everything the board shows, mapped by the game screen from its state.
 *
 * @property currentIndex the player whose column has the `accent` border and the "▶".
 * @property visits one visit per player (CR14), for the outlined box of each column header.
 * @property points the footer row, or `null` in a solo game (CR23).
 */
class CricketBoardContent(
    val rows: List<CricketBoardRow>,
    val names: List<String>,
    val slots: List<ColorSlot>,
    val currentIndex: Int,
    val visits: List<CricketVisit>,
    val points: List<Int>?,
)

/**
 * The marks board (CR23, design system `CricketBoard`): one column per player, one row per target, 20 to 15 then
 * Bull. Display only. The sizes come from [CricketLayout]. The column headers stay on top; the rows and the points
 * footer scroll alone when they do not fit. A dead row is struck through and in `onBackgroundMuted` (CR15, CR23); the
 * composable only draws what [CricketBoardText] decided.
 */
@Composable
fun CricketBoard(content: CricketBoardContent, layout: CricketLayout, modifier: Modifier = Modifier) {
    val colors = KosmoTheme.colors
    val texts = remember(content.rows) { CricketBoardText.of(content.rows) }
    Column(
        modifier = modifier
            .testTag(CricketTags.BOARD)
            .semantics { isTraversalGroup = true }
            .clip(KosmoShapes.card)
            .background(colors.surface),
    ) {
        HeaderRow(content, layout)
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .testTag(CricketTags.BOARD_ROWS)
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = ROWS_TRAVERSAL_INDEX
                }
                .verticalScroll(rememberScrollState()),
        ) {
            content.rows.forEachIndexed { index, row -> BoardRow(row, texts[index], content, layout) }
            content.points?.let { PointsRow(it, content, layout) }
        }
    }
}

/** The sticky column headers: the player's color strip, his name and the outlined box of his last visit. */
@Composable
private fun HeaderRow(content: CricketBoardContent, layout: CricketLayout) {
    Row(Modifier.fillMaxWidth().height(layout.columnHeaderHeightDp.dp)) {
        Box(Modifier.width(layout.labelColumnWidthDp.dp))
        content.names.indices.forEach { player ->
            HeaderCell(player, content, layout, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun HeaderCell(player: Int, content: CricketBoardContent, layout: CricketLayout, modifier: Modifier) {
    val colors = KosmoTheme.colors
    val isCurrent = player == content.currentIndex
    val name = content.names[player]
    val visit = content.visits[player]
    val visitText = visitDescription(visit)
    val description = if (isCurrent) {
        val current = stringResource(R.string.cricket_board_column_current, name)
        stringResource(R.string.cricket_board_column_other, current, visitText)
    } else {
        stringResource(R.string.cricket_board_column_other, name, visitText)
    }
    val order = headerOrder(player, content)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .columnBorder(isCurrent, colors.accent, drawTop = true, drawBottom = false)
            .testTag(CricketTags.columnHeader(player))
            .clearAndSetSemantics {
                contentDescription = description
                traversalIndex = order
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(CricketLayoutPolicy.HEADER_STRIP.dp)
                .background(PlayerPalette.colorOf(content.slots[player], colors)),
        )
        Text(
            text = (if (isCurrent) "▶ " else "") + displayName(name),
            style = KosmoTheme.typography.label,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = NAME_MIN_FONT, maxFontSize = NAME_MAX_FONT),
            modifier = Modifier.fillMaxWidth().weight(1f).testTag(CricketTags.columnName(player)),
        )
        Spacer(Modifier.height(CricketLayoutPolicy.HEADER_GAP.dp))
        CricketMarksBox(
            visit = visit,
            isFilled = false,
            style = KosmoTheme.typography.label,
            tag = CricketTags.columnVisit(player),
            modifier = Modifier.height(layout.visitBoxHeightDp.dp),
        )
    }
}

/** TalkBack order of the headers (review U7): the current player's first, then the others in configuration order. */
private fun headerOrder(player: Int, content: CricketBoardContent): Float =
    if (player == content.currentIndex) 0f else (player + 1).toFloat()

@Composable
private fun BoardRow(
    row: CricketBoardRow,
    text: CricketRowText,
    content: CricketBoardContent,
    layout: CricketLayout,
) {
    val colors = KosmoTheme.colors
    val textColor = if (text.textColor == BoardTextColor.MUTED) colors.onBackgroundMuted else colors.onBackground
    val label = stringResource(text.labelRes, *text.labelArgs.toTypedArray())
    val deadDescription = if (row.isDead) stringResource(R.string.cricket_board_dead_row, label) else ""
    val outline = colors.outline
    val rowModifier = Modifier
        .fillMaxWidth()
        .height(layout.rowHeightDp.dp)
        .drawBehind { drawLine(outline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
        .testTag(CricketTags.row(row.target))
    Box(modifier = rowModifier.semantics { rowDead = row.isDead }) {
        // A dead row is one merged node, read once (CR23): its cells are not read one by one. The strike line is a
        // sibling of this node, so that clearing the cells' semantics does not remove it.
        val cellsModifier = if (row.isDead) {
            Modifier.clearAndSetSemantics { contentDescription = deadDescription }
        } else {
            Modifier
        }
        Row(cellsModifier.fillMaxWidth().fillMaxHeight()) {
            Text(
                text = label,
                style = rowStyle(),
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .width(layout.labelColumnWidthDp.dp)
                    .align(Alignment.CenterVertically)
                    .testTag(CricketTags.rowLabel(row.target)),
            )
            row.marks.forEachIndexed { player, marks ->
                val cell = Modifier.weight(1f).fillMaxHeight()
                BoardCell(row.target, label, player, CellMarks(marks, text.isClosed[player]), textColor, content, cell)
            }
        }
        if (text.isStruck) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(start = layout.labelColumnWidthDp.dp)
                    .height(STRIKE)
                    .background(colors.onBackgroundMuted)
                    .testTag(CricketTags.rowStrike(row.target)),
            )
        }
    }
}

@Composable
private fun BoardCell(
    target: CricketTarget,
    label: String,
    player: Int,
    cellMarks: CellMarks,
    color: Color,
    content: CricketBoardContent,
    modifier: Modifier,
) {
    val name = content.names[player]
    val (marks, isClosed) = cellMarks
    val description = if (isClosed) {
        stringResource(R.string.cricket_board_closed_cell, label, name)
    } else {
        pluralStringResource(R.plurals.cricket_cell_marks, marks, label, name, marks)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .columnBorder(player == content.currentIndex, KosmoTheme.colors.accent, drawTop = false, drawBottom = false)
            .testTag(CricketTags.cell(target, player))
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (marks > 0) MarkGlyph(marks, color, Modifier.testTag(CricketTags.mark(target, player)))
    }
}

/**
 * The mark of a cell, drawn (not typed) so that "/", "X" and "Ⓧ" have the same size whatever the font has: one
 * stroke, two crossed strokes, and the two strokes in a circle (CR3, CR23).
 */
@Composable
private fun MarkGlyph(marks: Int, color: Color, modifier: Modifier) {
    Canvas(modifier.size(MARK_SIZE)) {
        val stroke = Stroke(width = MARK_STROKE.toPx(), cap = StrokeCap.Round)
        val inset = if (CricketBoardText.isClosed(marks)) size.width * CROSS_INSET_CLOSED else 0f
        val low = inset + stroke.width / 2
        val high = size.width - inset - stroke.width / 2
        drawLine(color, Offset(low, high), Offset(high, low), stroke.width, StrokeCap.Round)
        if (marks >= 2) drawLine(color, Offset(low, low), Offset(high, high), stroke.width, StrokeCap.Round)
        if (CricketBoardText.isClosed(marks)) {
            drawCircle(color, radius = (size.width - stroke.width) / 2, style = stroke)
        }
    }
}

private const val CROSS_INSET_CLOSED = 0.22f

@Composable
private fun PointsRow(points: List<Int>, content: CricketBoardContent, layout: CricketLayout) {
    val colors = KosmoTheme.colors
    val separator = stringResource(R.string.cricket_board_points_separator)
    val entries = content.names.indices.map { player ->
        stringResource(R.string.cricket_board_points_entry, content.names[player], points[player])
    }.joinToString(separator)
    val description = stringResource(R.string.cricket_board_points, entries)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(layout.rowHeightDp.dp)
            .testTag(CricketTags.POINTS_ROW)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Box(Modifier.width(layout.labelColumnWidthDp.dp))
        points.forEachIndexed { player, value ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .columnBorder(player == content.currentIndex, colors.accent, drawTop = false, drawBottom = true),
            ) {
                Text(
                    text = value.toString(),
                    style = rowStyle(),
                    color = colors.onBackground,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag(CricketTags.points(player)),
                )
            }
        }
    }
}

@Composable
private fun rowStyle(): TextStyle =
    KosmoTheme.typography.labelLarge.copy(fontSize = CricketLayoutPolicy.ROW_LABEL_TEXT_SIZE.sp)

/** The `accent` border of the current player's column: both sides, and the top or the bottom where it ends. */
private fun Modifier.columnBorder(isCurrent: Boolean, color: Color, drawTop: Boolean, drawBottom: Boolean): Modifier =
    if (!isCurrent) {
        this
    } else {
        drawBehind {
            val width = COLUMN_BORDER.toPx()
            drawLine(color, Offset(width / 2, 0f), Offset(width / 2, size.height), width)
            drawLine(color, Offset(size.width - width / 2, 0f), Offset(size.width - width / 2, size.height), width)
            if (drawTop) drawLine(color, Offset(0f, width / 2), Offset(size.width, width / 2), width)
            if (drawBottom) {
                val y = size.height - width / 2
                drawLine(color, Offset(0f, y), Offset(size.width, y), width)
            }
        }
    }

/** The marks of one cell and whether they close the target, as decided by [CricketBoardText] (review A7). */
private data class CellMarks(val marks: Int, val isClosed: Boolean)
