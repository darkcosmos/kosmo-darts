package io.github.darkcosmos.kosmodarts.ui.cricket

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketTarget
import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/** Test tags of the Cricket game screen. "Miss" and the action bar reuse the X01 `KeypadTags`. */
object CricketTags {
    /** The whole screen content. */
    const val SCREEN: String = "cricket_screen"

    /** The board: its sticky column headers and its scrolling rows. */
    const val BOARD: String = "cricket_board"

    /** The rows of the board (they scroll only on a screen smaller than the Pixel 7). */
    const val BOARD_ROWS: String = "cricket_board_rows"

    /** The landscape title line over the zone (mode, position, S/M chips). */
    const val TITLE: String = "cricket_title"

    /** The white "VICTORY" banner of the zone (CR25). */
    const val WIN_BANNER: String = "cricket_win_banner"

    /** The marks per round of the zone. */
    const val ZONE_STAT: String = "cricket_zone_stat"

    /** One target row. */
    fun row(target: CricketTarget): String = "cricket_row_${id(target)}"

    /** The touch area of the button that records [dart]: the whole cell of its row. */
    fun dartButton(dart: Dart): String = "cricket_button_${id(dart)}"

    /** The drawn key of [dart]'s button, inset in its touch area. */
    fun dartKey(dart: Dart): String = "cricket_key_${id(dart)}"

    /** The dots drawn under the number of [dart]'s button. */
    fun dartDots(dart: Dart): String = "cricket_dots_${id(dart)}"

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

    /** The total of a player, at the top of his column header (absent in a solo game). */
    fun columnTotal(player: Int): String = "cricket_column_total_$player"
}

private fun id(target: CricketTarget): String = target.name.lowercase()

private fun id(dart: Dart): String = "${dart.segment}_${dart.multiplier.name.lowercase()}"
