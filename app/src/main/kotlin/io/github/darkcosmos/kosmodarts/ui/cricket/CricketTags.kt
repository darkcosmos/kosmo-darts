package io.github.darkcosmos.kosmodarts.ui.cricket

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketTarget

/** Test tags of the Cricket game screen. The keypad keys and the action bar reuse the X01 `KeypadTags`. */
object CricketTags {
    /** The whole screen content. */
    const val SCREEN: String = "cricket_screen"

    /** The board: its sticky column headers and its scrolling rows. */
    const val BOARD: String = "cricket_board"

    /** The scrolling part of the board: the rows and the points footer. */
    const val BOARD_ROWS: String = "cricket_board_rows"

    /** The points footer row (absent in a solo game, CR23). */
    const val POINTS_ROW: String = "cricket_points_row"

    /** The white "VICTORY" banner of the zone (CR25). */
    const val WIN_BANNER: String = "cricket_win_banner"

    /** The score of the zone: the points, or the closed targets in a solo game. */
    const val ZONE_SCORE: String = "cricket_zone_score"

    /** The marks per round of the zone. */
    const val ZONE_STAT: String = "cricket_zone_stat"

    /** The keypad of the screen. */
    const val KEYPAD: String = "cricket_keypad"

    private fun id(target: CricketTarget): String = target.name.lowercase()

    /** One target row. */
    fun row(target: CricketTarget): String = "cricket_row_${id(target)}"

    /** The label cell of a target row. */
    fun rowLabel(target: CricketTarget): String = "cricket_row_label_${id(target)}"

    /** The 2 dp strike-through line of a dead row (CR23): present on dead rows only. */
    fun rowStrike(target: CricketTarget): String = "cricket_row_strike_${id(target)}"

    /** The cell of [target] for the player at [player]. */
    fun cell(target: CricketTarget, player: Int): String = "cricket_cell_${id(target)}_$player"

    /** The drawn mark inside a cell: a fixed-size box, the same size for 1, 2 and 3 marks. */
    fun mark(target: CricketTarget, player: Int): String = "cricket_mark_${id(target)}_$player"

    /** The column header of a player: strip, name and visit box. */
    fun columnHeader(player: Int): String = "cricket_column_header_$player"

    /** The name inside a column header. */
    fun columnName(player: Int): String = "cricket_column_name_$player"

    /** The points of a player in the footer row. */
    fun points(player: Int): String = "cricket_points_$player"
}
