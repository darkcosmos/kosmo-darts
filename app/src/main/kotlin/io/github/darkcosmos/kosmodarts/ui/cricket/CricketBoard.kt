package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketTarget
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVisit
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.ui.format.displayName
import io.github.darkcosmos.kosmodarts.ui.game.KeypadTags
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.PlayerPalette

private val STRIKE = 2.dp
private val COLUMN_BORDER = 2.dp
private val MARK_SIZE = 18.dp
private val MARK_STROKE = 2.dp
private const val ROWS_TRAVERSAL_INDEX = 100f

/**
 * Everything the board shows, mapped by the game screen from its state.
 *
 * @property currentIndex the player whose column has the `accent` border and the "▶".
 * @property visits one visit per player (CR14), read in the TalkBack text of each column header.
 * @property totals the totals of the column headers, or `null` in a solo game (CR22, CR23).
 * @property buttons the buttons of each row, in the order of [rows] (CR24).
 * @property isMissEnabled whether "Miss" accepts a tap (false while a turn waits or a win is pending).
 */
class CricketBoardContent(
    val rows: List<CricketBoardRow>,
    val names: List<String>,
    val slots: List<ColorSlot>,
    val currentIndex: Int,
    val visits: List<CricketVisit>,
    val totals: List<Int>?,
    val buttons: List<List<CricketTargetButton>>,
    val isMissEnabled: Boolean,
)

/**
 * The marks board (CR23, CR24, design system `CricketBoard`): the player columns, then the button column at the end;
 * one row per target, 20 to 15 then Bull. The pinned header shows each player's total over his name, and "Miss" alone
 * at the top of the button column. The sizes come from [CricketBoardLayoutPolicy]; the rows scroll under the header
 * only when [CricketBoardLayout.scrolls] (a screen smaller than the Pixel 7). A dead row is struck through the player
 * cells and in `onBackgroundMuted` (CR15, CR23); the composable only draws what [CricketBoardText] decided.
 */
@Composable
fun CricketBoard(
    content: CricketBoardContent,
    layout: CricketBoardLayout,
    header: CricketHeaderMetrics,
    onDart: (Dart) -> Unit,
    onMiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KosmoTheme.colors
    val texts = remember(content.rows) { CricketBoardText.of(content.rows) }
    val scroll = if (layout.scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(
        modifier = modifier
            .testTag(CricketTags.BOARD)
            .semantics { isTraversalGroup = true }
            .clip(KosmoShapes.card)
            .background(colors.surface),
    ) {
        HeaderRow(content, layout, header, onMiss)
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .testTag(CricketTags.BOARD_ROWS)
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = ROWS_TRAVERSAL_INDEX
                }
                .then(scroll),
        ) {
            content.rows.forEachIndexed { index, row ->
                BoardRow(row, texts[index], content.buttons[index], content, layout, onDart)
            }
        }
    }
}

/** The pinned header: a column per player (strip, total, name), then "Miss" over the button column (CR22, Q9 B). */
@Composable
private fun HeaderRow(
    content: CricketBoardContent,
    layout: CricketBoardLayout,
    header: CricketHeaderMetrics,
    onMiss: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(layout.missCellHeightDp.dp)) {
        content.names.indices.forEach { player ->
            HeaderCell(player, content, header, Modifier.weight(1f).fillMaxHeight())
        }
        MissCell(content, layout, onMiss)
    }
}

@Composable
private fun MissCell(content: CricketBoardContent, layout: CricketBoardLayout, onMiss: () -> Unit) {
    val color = KosmoTheme.colors.onBackgroundMuted
    // After the last header and before the rows (CR23).
    val order = (content.names.size + 1).toFloat()
    BoardKey(
        description = stringResource(R.string.dart_miss),
        tags = KeyTags(KeypadTags.MISS, CricketTags.dartKey(Dart.MISS)),
        isEnabled = content.isMissEnabled,
        onClick = onMiss,
        isOutlined = true,
        modifier = Modifier
            .width(layout.missCellWidthDp.dp)
            .fillMaxHeight()
            .semantics { traversalIndex = order },
    ) {
        Text(
            text = stringResource(R.string.dart_miss),
            style = KosmoTheme.typography.keypadLabel,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun HeaderCell(player: Int, content: CricketBoardContent, header: CricketHeaderMetrics, modifier: Modifier) {
    val colors = KosmoTheme.colors
    val isCurrent = player == content.currentIndex
    val name = content.names[player]
    val total = content.totals?.get(player)
    val visitText = visitDescription(content.visits[player])
    val who = if (isCurrent) stringResource(R.string.cricket_board_column_current, name) else name
    val withPoints = if (total == null) {
        who
    } else {
        val points = pluralStringResource(R.plurals.cricket_header_points, total, total)
        stringResource(R.string.cricket_board_column_other, who, points)
    }
    val description = stringResource(R.string.cricket_board_column_other, withPoints, visitText)
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
                .height(CricketBoardLayoutPolicy.STRIP_HEIGHT.dp)
                .background(PlayerPalette.colorOf(content.slots[player], colors)),
        )
        if (total != null && header.totalSize != null) {
            val style = totalStyle(KosmoTheme.typography.tileScore, header.totalSize, LocalDensity.current)
            Text(
                text = total.toString(),
                style = style,
                color = colors.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.fillMaxWidth().testTag(CricketTags.columnTotal(player)),
            )
        }
        Text(
            text = (if (isCurrent) "▶ " else "") + displayName(name),
            style = KosmoTheme.typography.label,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .wrapContentHeight(Alignment.CenterVertically)
                .testTag(CricketTags.columnName(player)),
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
    buttons: List<CricketTargetButton>,
    content: CricketBoardContent,
    layout: CricketBoardLayout,
    onDart: (Dart) -> Unit,
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
        Row(Modifier.fillMaxSize()) {
            // A dead row's cells are one merged node, read once (CR23); its buttons stay outside it, enabled (CR15).
            val cellsModifier = if (row.isDead) {
                Modifier.clearAndSetSemantics { contentDescription = deadDescription }
            } else {
                Modifier
            }
            Row(cellsModifier.weight(1f).fillMaxHeight()) {
                row.marks.forEachIndexed { player, marks ->
                    val cell = Modifier.weight(1f).fillMaxHeight()
                    val cellMarks = CellMarks(marks, text.isClosed[player])
                    BoardCell(row.target, label, player, cellMarks, textColor, content, cell)
                }
            }
            CricketTargetButtons(buttons, row.isDead, layout.buttonColumnWidthDp, onDart)
        }
        // The strike crosses the player cells only: the buttons must not look disabled. It is a sibling of the cells,
        // so that clearing their semantics does not remove it.
        if (text.isStruck) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(end = layout.buttonColumnWidthDp.dp)
                    .fillMaxWidth()
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
