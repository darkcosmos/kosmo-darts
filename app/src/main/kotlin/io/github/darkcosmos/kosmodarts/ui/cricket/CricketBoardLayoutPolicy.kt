package io.github.darkcosmos.kosmodarts.ui.cricket

import kotlin.math.max
import kotlin.math.min

/**
 * A candidate size of a column total: [sizeSp] and the em of that size measured in dp at the current font scale (the
 * screen reads it with `Density`, which applies Android's non-linear font scaling).
 */
data class TotalSizeCandidate(val sizeSp: Float, val emDp: Float)

/**
 * The size picked for a total. [isFixed] is true for the fallback, which the screen draws with `density.fixedSp` so
 * that it does not follow the font scale.
 */
data class TotalSize(val sizeSp: Float, val isFixed: Boolean)

/**
 * Sizes of the Cricket board in portrait, in dp.
 *
 * @property rowHeightDp height of a target row, 48 to 56 dp.
 * @property scrolls whether the target rows scroll under the pinned header (fallback below the Pixel 7 only).
 * @property playerColumnWidthDp width of each player column.
 * @property buttonColumnWidthDp width of the button column, at the end of the board.
 * @property buttonWidthDp width of one target button (a third of the button column).
 * @property missCellWidthDp width of the "RATE" cell: the button column's.
 * @property missCellHeightDp height of the "RATE" cell: the column header's, 48 dp at least.
 */
data class CricketBoardLayout(
    val rowHeightDp: Float,
    val scrolls: Boolean,
    val playerColumnWidthDp: Float,
    val buttonColumnWidthDp: Float,
    val buttonWidthDp: Float,
    val missCellWidthDp: Float,
    val missCellHeightDp: Float,
)

/**
 * Sizes of the Cricket screen in landscape (bars hidden), in dp.
 *
 * @property board the board's sizes; its header is the band.
 * @property bandHeightDp one height for the board header and for the right column's title plus zone line.
 * @property boardWidthDp width of the board (player columns then button column).
 * @property rightColumnWidthDp width of the right column (title, zone, action bar), after the [CricketBoardLayoutPolicy.BOARD_GAP].
 * @property barButtonWidthDp width of each of the two side-by-side action bar buttons.
 * @property barRoomHeightDp height under the band for the action bar.
 */
data class CricketLandscapeLayout(
    val board: CricketBoardLayout,
    val bandHeightDp: Float,
    val boardWidthDp: Float,
    val rightColumnWidthDp: Float,
    val barButtonWidthDp: Float,
    val barRoomHeightDp: Float,
)

/**
 * Measured dp values of the landscape screen (bars hidden): [cutoutInsetDp] is the display cutout on one side,
 * [headerHeightDp] the board's column header, [titleLineDp] and [zoneLineDp] the right column's title and
 * single-line zone.
 */
data class CricketLandscapeSpace(
    val screenWidthDp: Float,
    val screenHeightDp: Float,
    val cutoutInsetDp: Float,
    val headerHeightDp: Float,
    val titleLineDp: Float,
    val zoneLineDp: Float,
    val playerCount: Int,
    val rowCount: Int,
)

/**
 * Portrait split of revision 2 (plan R2.1): the board's sizes and the zone's height.
 *
 * @property board the board's sizes (rows 48 to 56 dp).
 * @property zoneHeightDp height of the player zone: all the height the header and the rows leave.
 * @property showsTotal whether the zone's middle is tall enough (`ZONE_TOTAL_MIN`) to show the big total; false means
 *   the two-line zone (name line, dart cells), the total staying in the column header.
 * @property totalAreaHeightDp height left for the big total (zone minus its fixed parts), 0 when [showsTotal] is false.
 */
data class CricketPortraitSplit(
    val board: CricketBoardLayout,
    val zoneHeightDp: Float,
    val showsTotal: Boolean,
    val totalAreaHeightDp: Float,
)

/**
 * Landscape layout of revision 2 (plan R2.2): top band, header row, rows, action column, bars hidden.
 *
 * @property board the board's sizes; its rows are 42 to 56 dp, its miss cell is the "RATE" key.
 * @property bandHeightDp the top band, from the top edge.
 * @property headerRowHeightDp height of the header row ("RATE", "ANNULER" and the column headers), 48 dp at least.
 * @property boardWidthDp width of the player columns and of the button column.
 * @property actionColumnWidthDp width of the action column ("ANNULER" and the tall "JOUEUR SUIVANT").
 * @property undoWidthDp width of "ANNULER".
 * @property nextKeyHeightDp height of the tall next key: the visible height of the rows.
 * @property rowsViewportHeightDp height under the band and the header row, up to the bottom margin.
 */
data class CricketLandscapeBandLayout(
    val board: CricketBoardLayout,
    val bandHeightDp: Float,
    val headerRowHeightDp: Float,
    val boardWidthDp: Float,
    val actionColumnWidthDp: Float,
    val undoWidthDp: Float,
    val nextKeyHeightDp: Float,
    val rowsViewportHeightDp: Float,
)

/**
 * Measured dp values of the landscape screen of revision 2: [cutoutInsetDp] is the display cutout on one side and
 * [headerHeightDp] the board's two-line column header (strip, total, name).
 */
data class CricketLandscapeBandSpace(
    val screenWidthDp: Float,
    val screenHeightDp: Float,
    val cutoutInsetDp: Float,
    val headerHeightDp: Float,
    val playerCount: Int,
    val rowCount: Int,
)

/** The portrait zone's fixed part: 2 x 6 dp padding, the name line, 2 x 8 dp gaps and the 46 dp dart cells. */
fun zoneFixedHeightDp(nameLineDp: Float): Float =
    2 * CricketBoardLayoutPolicy.ZONE_PADDING + nameLineDp + 2 * CricketBoardLayoutPolicy.ZONE_GAP +
        CricketBoardLayoutPolicy.ZONE_DART_CELL

/**
 * Sizes of the Cricket board for the Pixel 7 (411 x 914 dp, docs/rules/cricket.md CR22, plan section 4). It does no
 * font-scale arithmetic: the screen measures the texts with Compose and passes dp values. No Compose type here, so it
 * is tested on the JVM.
 */
object CricketBoardLayoutPolicy {

    const val ROW_HEIGHT_MIN = 48f
    const val ROW_HEIGHT_MAX = 56f
    const val BUTTON_WIDTH_MIN = 48f
    const val BUTTON_WIDTH_MAX = 56f
    const val BUTTON_COLUMN_WIDTH_MIN = 3 * BUTTON_WIDTH_MIN
    const val BUTTON_COLUMN_WIDTH_MAX = 3 * BUTTON_WIDTH_MAX
    const val PLAYER_COLUMN_WIDTH = 76f
    const val STRIP_HEIGHT = 3f
    const val SIDE_MARGIN = 12f
    const val BOARD_GAP = 12f
    const val BAR_GAP = 12f
    const val TOP_MARGIN = 8f
    const val BOTTOM_MARGIN = 8f
    const val GAP = 6f
    const val LANDSCAPE_MARGIN = 4f
    const val LANDSCAPE_TITLE_GAP = 4f

    /** Landscape zone line (name, MPR, dart slots), the fixed dart text size in it, and the S/M chips' padding. */
    const val LANDSCAPE_ZONE_LINE = 28f
    const val LANDSCAPE_DART_SP = 14f
    const val CHIPS_VERTICAL_PADDING = 4f

    /** The 14 sp body line at font scale 1.0, and the portrait zone height that goes with it. */
    const val BODY_LINE_BASE = 22.5f
    const val ZONE_HEIGHT = 84f
    const val ZONE_GROWTH_MAX = 1.5f

    /** Tolerance for float comparisons of heights computed from a division. */
    private const val EPSILON = 0.001f

    /** Revision 2, portrait zone: the total needs 32 dp; fixed parts of the zone (padding, gaps, dart cells). */
    const val ZONE_TOTAL_MIN = 32f
    const val ZONE_PADDING = 6f
    const val ZONE_GAP = 8f
    const val ZONE_DART_CELL = 46f

    /** Revision 2, landscape: band, action column, row floor (Kosmo, Q14 B), key floor, bottom margin. */
    const val LANDSCAPE_BAND = 44f
    const val LANDSCAPE_ACTION_WIDTH = 120f
    const val LANDSCAPE_ROW_MIN = 42f
    const val LANDSCAPE_BOTTOM_MARGIN = 4f
    const val LANDSCAPE_KEY_MIN = 48f

    /** A digit is 0.6 em wide; the line has 4 dp of padding. Only here: callers never repeat them. */
    const val DIGIT_EM = 0.6f
    const val TOTAL_PADDING_DP = 4f

    /** The fixed size used when no candidate fits the column, so a total is never cut. */
    const val TOTAL_FALLBACK_SP = 14f

    val PORTRAIT_TOTAL_SIZES_SP: List<Float> = listOf(24f, 20f, 16f, 14f)
    val LANDSCAPE_TOTAL_SIZES_SP: List<Float> = listOf(20f, 16f, 14f)

    /** Width of a total of [digits] digits at [emDp], padding included. */
    fun totalLineWidthDp(digits: Int, emDp: Float): Float = digits * DIGIT_EM * emDp + TOTAL_PADDING_DP

    /**
     * The largest candidate (candidates from the largest to the smallest size) whose line fits [columnWidthDp], or the
     * fixed [TOTAL_FALLBACK_SP] when none fits.
     */
    fun totalTextSize(digits: Int, columnWidthDp: Float, candidates: List<TotalSizeCandidate>): TotalSize {
        val fitting = candidates.firstOrNull { totalLineWidthDp(digits, it.emDp) <= columnWidthDp }
        return if (fitting != null) TotalSize(fitting.sizeSp, isFixed = false) else TotalSize(TOTAL_FALLBACK_SP, true)
    }

    /** The column header: strip, the total line (null in a solo game: no total) and the name line. */
    fun columnHeaderHeightDp(totalLineDp: Float?, nameLineDp: Float): Float =
        STRIP_HEIGHT + (totalLineDp ?: 0f) + nameLineDp

    /** The portrait zone: 84 dp times the growth of the 14 sp line, capped at 1.5. */
    fun portraitZoneHeightDp(bodyLineDp: Float): Float =
        ZONE_HEIGHT * min(max(bodyLineDp / BODY_LINE_BASE, 1f), ZONE_GROWTH_MAX)

    /** Height left for the column header and the rows: the screen minus margins, gaps and the pinned parts. */
    fun portraitBoardHeightDp(screenHeightDp: Float, matchHeaderDp: Float, zoneDp: Float, barDp: Float): Float =
        screenHeightDp - TOP_MARGIN - matchHeaderDp - GAP - zoneDp - GAP - GAP - barDp - BOTTOM_MARGIN

    /** Width of the player columns and of the button column for [boardWidthDp] (the screen minus its side margins). */
    fun columnWidths(boardWidthDp: Float, playerCount: Int): Pair<Float, Float> {
        val roomForPlayers = boardWidthDp - BUTTON_COLUMN_WIDTH_MAX
        val buttons = if (roomForPlayers / playerCount >= PLAYER_COLUMN_WIDTH) {
            BUTTON_COLUMN_WIDTH_MAX
        } else {
            (boardWidthDp - PLAYER_COLUMN_WIDTH * playerCount)
                .coerceIn(BUTTON_COLUMN_WIDTH_MIN, BUTTON_COLUMN_WIDTH_MAX)
        }
        return (boardWidthDp - buttons) / playerCount to buttons
    }

    /**
     * Portrait board: [screenWidthDp] is the whole width. [boardHeightDp] is the height left for the header and the
     * rows ([portraitBoardHeightDp]).
     */
    fun portrait(
        screenWidthDp: Float,
        boardHeightDp: Float,
        headerHeightDp: Float,
        playerCount: Int,
        rowCount: Int,
    ): CricketBoardLayout =
        layout(screenWidthDp - 2 * SIDE_MARGIN, boardHeightDp, headerHeightDp, playerCount, rowCount)

    /**
     * Revision 2 portrait (plan R2.1). [availableHeightDp] is the height shared by the zone and the board (the screen
     * minus margins, `MatchHeader`, gaps and the action bar). The rows give up their extra height first so that the
     * total keeps [ZONE_TOTAL_MIN]; the zone takes all the height left. When even 48 dp rows leave the total less than
     * that, the zone is the two-line zone (no total). Below the two-line zone the rows keep 48 dp and scroll.
     */
    fun portraitSplit(
        screenWidthDp: Float,
        availableHeightDp: Float,
        headerHeightDp: Float,
        nameLineDp: Float,
        playerCount: Int,
        rowCount: Int,
    ): CricketPortraitSplit {
        val fixed = zoneFixedHeightDp(nameLineDp)
        val rowHeight = ((availableHeightDp - headerHeightDp - fixed - ZONE_TOTAL_MIN) / rowCount)
            .coerceIn(ROW_HEIGHT_MIN, ROW_HEIGHT_MAX)
        val zone = availableHeightDp - headerHeightDp - rowHeight * rowCount
        val scrolls = zone < fixed - EPSILON
        val zoneHeight = if (scrolls) fixed else zone
        val area = zoneHeight - fixed
        val showsTotal = !scrolls && area >= ZONE_TOTAL_MIN - EPSILON
        val (playerWidth, buttonColumn) = columnWidths(screenWidthDp - 2 * SIDE_MARGIN, playerCount)
        return CricketPortraitSplit(
            board = CricketBoardLayout(
                rowHeightDp = rowHeight,
                scrolls = scrolls,
                playerColumnWidthDp = playerWidth,
                buttonColumnWidthDp = buttonColumn,
                buttonWidthDp = buttonColumn / 3,
                missCellWidthDp = buttonColumn,
                missCellHeightDp = max(headerHeightDp, ROW_HEIGHT_MIN),
            ),
            zoneHeightDp = zoneHeight,
            showsTotal = showsTotal,
            totalAreaHeightDp = if (showsTotal) area else 0f,
        )
    }

    /**
     * Revision 2 landscape (plan R2.2, bars hidden): band, header row (the measured header, 48 dp at least), rows
     * 42 to 56 dp (they scroll below 42 dp), 56 dp buttons, a 12 dp gap and the 120 dp action column. The player
     * columns share the width left.
     */
    fun landscapeBand(space: CricketLandscapeBandSpace): CricketLandscapeBandLayout = with(space) {
        val headerRow = max(headerHeightDp, LANDSCAPE_KEY_MIN)
        val viewport = screenHeightDp - LANDSCAPE_BOTTOM_MARGIN - LANDSCAPE_BAND - headerRow
        val rowHeight = (viewport / rowCount).coerceIn(LANDSCAPE_ROW_MIN, ROW_HEIGHT_MAX)
        val scrolls = viewport < LANDSCAPE_ROW_MIN * rowCount - EPSILON
        val width = screenWidthDp - 2 * SIDE_MARGIN - cutoutInsetDp
        val playerWidth = (width - BUTTON_COLUMN_WIDTH_MAX - BOARD_GAP - LANDSCAPE_ACTION_WIDTH) / playerCount
        CricketLandscapeBandLayout(
            board = CricketBoardLayout(
                rowHeightDp = rowHeight,
                scrolls = scrolls,
                playerColumnWidthDp = playerWidth,
                buttonColumnWidthDp = BUTTON_COLUMN_WIDTH_MAX,
                buttonWidthDp = BUTTON_WIDTH_MAX,
                missCellWidthDp = BUTTON_COLUMN_WIDTH_MAX,
                missCellHeightDp = headerRow,
            ),
            bandHeightDp = LANDSCAPE_BAND,
            headerRowHeightDp = headerRow,
            boardWidthDp = playerWidth * playerCount + BUTTON_COLUMN_WIDTH_MAX,
            actionColumnWidthDp = LANDSCAPE_ACTION_WIDTH,
            undoWidthDp = LANDSCAPE_ACTION_WIDTH,
            nextKeyHeightDp = if (scrolls) viewport else rowHeight * rowCount,
            rowsViewportHeightDp = viewport,
        )
    }

    /** Landscape (bars hidden), see [CricketLandscapeSpace]. */
    fun landscape(space: CricketLandscapeSpace): CricketLandscapeLayout = with(space) {
        val band = max(headerHeightDp, titleLineDp + LANDSCAPE_TITLE_GAP + zoneLineDp)
        val boardWidth = PLAYER_COLUMN_WIDTH * playerCount + BUTTON_COLUMN_WIDTH_MAX
        val rightWidth = screenWidthDp - 2 * SIDE_MARGIN - cutoutInsetDp - boardWidth - BOARD_GAP
        val boardHeight = screenHeightDp - 2 * LANDSCAPE_MARGIN - band
        CricketLandscapeLayout(
            board = layout(boardWidth, boardHeight + band, band, playerCount, rowCount),
            bandHeightDp = band,
            boardWidthDp = boardWidth,
            rightColumnWidthDp = rightWidth,
            barButtonWidthDp = (rightWidth - BAR_GAP) / 2,
            barRoomHeightDp = boardHeight - GAP,
        )
    }

    private fun layout(
        boardWidthDp: Float,
        boardHeightDp: Float,
        headerHeightDp: Float,
        playerCount: Int,
        rowCount: Int,
    ): CricketBoardLayout {
        val (playerWidth, buttonColumn) = columnWidths(boardWidthDp, playerCount)
        val rowsRoom = boardHeightDp - headerHeightDp
        val rowHeight = (rowsRoom / rowCount).coerceIn(ROW_HEIGHT_MIN, ROW_HEIGHT_MAX)
        return CricketBoardLayout(
            rowHeightDp = rowHeight,
            scrolls = rowsRoom < ROW_HEIGHT_MIN * rowCount,
            playerColumnWidthDp = playerWidth,
            buttonColumnWidthDp = buttonColumn,
            buttonWidthDp = buttonColumn / 3,
            missCellWidthDp = buttonColumn,
            missCellHeightDp = max(headerHeightDp, ROW_HEIGHT_MIN),
        )
    }
}
