package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.annotation.StringRes
import io.github.darkcosmos.kosmodarts.R

/** Whether a board row is still in play or dead (docs/rules/cricket.md, CR15, CR23). */
enum class RowStyle { LIVE, DEAD }

/** The text color of a board row (CR23): `onBackground` for a live row, `onBackgroundMuted` for a dead one. */
enum class BoardTextColor { PRIMARY, MUTED }

/**
 * What the board draws for one row (CR23, review T3). Decided here, in pure code: the composable only draws it.
 *
 * @property labelRes the target label, resolved with [labelArgs] ("20" to "15", or "BULL").
 * @property cells one mark glyph per player, in configuration order: empty, "/", "X" or "Ⓧ".
 * @property isClosed per player: whether the cell closes the target (3 marks), read "closed" and not "N marks".
 * @property isStruck whether a 2 dp line crosses the whole row: the non-color cue of a dead row.
 */
data class CricketRowText(
    @StringRes val labelRes: Int,
    val labelArgs: List<Int>,
    val cells: List<String>,
    val isClosed: List<Boolean>,
    val style: RowStyle,
    val textColor: BoardTextColor,
    val isStruck: Boolean,
)

/** Maps the rows of the Cricket board to their text and style (CR23). Pure. */
object CricketBoardText {

    private const val CLOSING_MARKS = 3

    /** The glyphs of 0, 1, 2 and 3 marks (CR3: 3 marks close the target). */
    val MARK_GLYPHS: List<String> = listOf("", "/", "X", "Ⓧ")

    /** One row text per row, in the same order. */
    fun of(rows: List<CricketBoardRow>): List<CricketRowText> = rows.map(::of)

    fun of(row: CricketBoardRow): CricketRowText {
        val isNumber = row.target.isNumber
        return CricketRowText(
            labelRes = if (isNumber) R.string.game_visit_value else R.string.dart_bull,
            labelArgs = if (isNumber) listOf(row.target.points) else emptyList(),
            cells = row.marks.map(::glyphOf),
            isClosed = row.marks.map(::isClosed),
            style = if (row.isDead) RowStyle.DEAD else RowStyle.LIVE,
            textColor = if (row.isDead) BoardTextColor.MUTED else BoardTextColor.PRIMARY,
            isStruck = row.isDead,
        )
    }

    /** Whether [marks] close a target (CR3: 3 marks or more). */
    fun isClosed(marks: Int): Boolean = marks >= CLOSING_MARKS

    /** The glyph of [marks], capped at the closing glyph (the engine caps marks at 3, CR3). */
    fun glyphOf(marks: Int): String = MARK_GLYPHS[marks.coerceIn(0, MARK_GLYPHS.lastIndex)]
}
