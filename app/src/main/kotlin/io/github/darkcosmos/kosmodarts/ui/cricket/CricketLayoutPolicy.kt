package io.github.darkcosmos.kosmodarts.ui.cricket

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * What the Cricket game screen knows about its space, in dp after the insets (docs/rules/cricket.md, CR22).
 *
 * @property textScale the effective font scale, as for the X01 `GameSpace` (1.0 to 2.0).
 * @property labelScale the effective scale of the 14 sp board texts (labels, names, visit boxes, points): Android
 *   enlarges small text more than large text, so it can be above [textScale] (review U1, U2).
 * @property rowCount the target rows of the board: 6 or 7 (CR1).
 * @property hasPointsRow whether the board has a points footer row: absent in a solo game (CR23).
 */
data class CricketSpace(
    val widthDp: Float,
    val heightDp: Float,
    val textScale: Float,
    val playerCount: Int,
    val rowCount: Int,
    val hasPointsRow: Boolean,
    val labelScale: Float = textScale,
) {
    val isLandscape: Boolean
        get() = widthDp > heightDp
}

/**
 * The sizes of the Cricket game screen, in dp. Composables only apply them.
 *
 * @property boardScrolls whether the board region is expected to scroll: its rows at their floor do not fit.
 */
data class CricketLayout(
    val keyHeightDp: Float,
    val actionBarMinHeightDp: Float,
    val gapDp: Float,
    val topMarginDp: Float,
    val bottomMarginDp: Float,
    val zoneHeightDp: Float,
    val zoneMinHeightDp: Float,
    val headerHeightDp: Float,
    val rowHeightDp: Float,
    val columnHeaderHeightDp: Float,
    val labelColumnWidthDp: Float,
    val playerColumnWidthDp: Float,
    val boardWidthDp: Float,
    val boardScrolls: Boolean,
)

/**
 * Sizes of the Cricket game screen as a pure function of its space (CR22, review U1). The keypad and the action bar are
 * fixed parts, never scrolled; the board takes the rest and scrolls alone when its rows at their floor do not fit.
 * Steps, each applied only if the previous one is not enough:
 * 1. keys from 54 down to 48 dp and the action bar from 58 down to 48 dp (never below: touch targets);
 * 2. top and bottom margins from 8 down to 4 and 0 dp, gaps from 6 down to 4 dp;
 * 3. the compact zone from 96 down to 76 dp (times its growth at large font scales);
 * 4. the board board rows from 32 down to their floor (24 dp, or the text line plus 2 dp).
 *
 * No Compose type here, so it is tested on the JVM.
 */
object CricketLayoutPolicy {

    const val KEY_HEIGHT = 54f
    const val KEY_HEIGHT_MIN = 48f
    const val ACTION_BAR_HEIGHT = 58f
    const val ACTION_BAR_HEIGHT_MIN = 48f
    const val GAP = 6f
    const val GAP_MIN = 4f
    const val TOP_MARGIN = 8f
    const val TOP_MARGIN_MIN = 4f
    const val BOTTOM_MARGIN = 8f
    const val BOTTOM_MARGIN_MIN = 0f
    const val ZONE_HEIGHT = 96f
    const val ZONE_HEIGHT_MIN = 76f
    const val HEADER_HEIGHT = 24f
    const val ROW_HEIGHT = 32f
    const val ROW_HEIGHT_MIN = 24f
    const val LABEL_COLUMN_WIDTH = 40f
    /** The text size of the row labels in sp: small enough for "BULL" to fit its column at every font scale. */
    const val ROW_LABEL_TEXT_SIZE = 14f

    /** The width of one player's column at least, and the one the landscape board column is sized for. */
    const val PLAYER_COLUMN_WIDTH = 76f

    /** Horizontal screen margin, as `Spacing.screenHorizontal`. */
    const val SCREEN_MARGIN = 12f

    /** The gap between the two columns in landscape, as the X01 screen. */
    const val COLUMN_GAP = 12f

    /** Rows of keys of the keypad (CR22). */
    const val KEYPAD_ROWS = 3

    // Heights (dp at font scale 1) of the board parts that follow the font scale. The line of the 14 sp display font
    // is measured at 22 dp, taken as 22.5 dp for rounding (about 1.6 em: the font has tall metrics and the
    // `lineHeight` of the style does not shrink it), so it is what the sizes are built from, not the nominal 1.2 em
    // (review U1).
    private const val ROW_TEXT_LINE = 22.5f
    private const val ROW_PADDING = 2f
    const val HEADER_STRIP = 3f
    private const val HEADER_NAME_LINE = 22.5f
    private const val BULL_WIDTH_EM = 2.5f
    private const val LABEL_PADDING = 5f
    private const val ZONE_GROWTH_MAX = 1.5f
    private const val LANDSCAPE_BOARD_MAX_SHARE = 0.55f
    private const val GAPS_PORTRAIT = 4
    private const val GAPS_LANDSCAPE_RIGHT = 2

    /** Height of a board row at the floor: `max(24, text line + 2)` (CR22). */
    fun rowFloor(textScale: Float): Float = max(ROW_HEIGHT_MIN, ROW_TEXT_LINE * textScale + ROW_PADDING)

    /** Height of the column header: the color strip and the name line, from the real line height. */
    fun columnHeaderHeight(textScale: Float): Float = HEADER_STRIP + HEADER_NAME_LINE * textScale

    /** The compact zone grows up to 1.5 times its height at font scale 2.0 and more (CR22). */
    fun zoneGrowth(textScale: Float): Float =
        (1f + (textScale - 1f) * (ZONE_GROWTH_MAX - 1f)).coerceIn(1f, ZONE_GROWTH_MAX)

    /** The label column fits "BULL", the widest label, at the label text size and font scale (review U2). */
    fun labelColumnWidth(textScale: Float): Float =
        max(LABEL_COLUMN_WIDTH, ceil(BULL_WIDTH_EM * ROW_LABEL_TEXT_SIZE * textScale) + LABEL_PADDING)

    fun of(space: CricketSpace): CricketLayout =
        if (space.isLandscape) landscape(space) else portrait(space)

    private fun boardRows(space: CricketSpace): Int = space.rowCount + if (space.hasPointsRow) 1 else 0

    private fun portrait(space: CricketSpace): CricketLayout {
        val scale = space.textScale
        val growth = zoneGrowth(scale)
        val header = HEADER_HEIGHT * scale
        val needed = columnHeaderHeight(space.labelScale) + boardRows(space) * ROW_HEIGHT
        val zone = ZONE_HEIGHT * growth
        val stages = listOf(
            fixedAt(0, zone),
            fixedAt(1, zone),
            fixedAt(2, zone),
            fixedAt(2, ZONE_HEIGHT_MIN * growth),
        )
        fun boardSpace(fixed: Fixed): Float = space.heightDp - header - fixed.portraitFixedHeight()
        val fixed = stages.firstOrNull { boardSpace(it) >= needed } ?: stages.last()
        val available = boardSpace(fixed)
        val rowFloor = rowFloor(space.labelScale)
        val header2 = columnHeaderHeight(space.labelScale)
        val rowHeight = ((available - header2) / boardRows(space)).coerceIn(rowFloor, max(rowFloor, ROW_HEIGHT))
        val scrolls = header2 + boardRows(space) * rowFloor > available
        val widths = columnWidths(space, space.widthDp - 2 * SCREEN_MARGIN)
        return fixed.toLayout(ZONE_HEIGHT_MIN * growth, header, rowHeight, space.labelScale, widths, scrolls)
    }

    private fun landscape(space: CricketSpace): CricketLayout {
        val scale = space.textScale
        val growth = zoneGrowth(scale)
        val label = labelColumnWidth(space.labelScale)
        val wanted = label + space.playerCount * PLAYER_COLUMN_WIDTH
        val boardWidth = min(wanted, (space.widthDp - 2 * SCREEN_MARGIN) * LANDSCAPE_BOARD_MAX_SHARE)
        val header = HEADER_HEIGHT * scale
        val zoneHeight = ZONE_HEIGHT * growth
        // Right column: zone, keypad and action bar. Keys and the action bar give way to their floors first.
        val nominal = fixedAt(0, zoneHeight)
        val zoneMin = ZONE_HEIGHT_MIN * growth
        val fixed = if (space.heightDp - nominal.landscapeFixedHeight() >= zoneMin) nominal else fixedAt(2, zoneHeight)
        val zone = (space.heightDp - fixed.landscapeFixedHeight()).coerceIn(zoneMin, zoneHeight)
        val columnHeader = columnHeaderHeight(space.labelScale)
        val available = space.heightDp - header - fixed.topMarginDp - fixed.bottomMarginDp - fixed.gapDp
        val rowFloor = rowFloor(space.labelScale)
        val rowHeight = ((available - columnHeader) / boardRows(space)).coerceIn(rowFloor, max(rowFloor, ROW_HEIGHT))
        val scrolls = columnHeader + boardRows(space) * rowFloor > available
        val widths = columnWidths(space, boardWidth)
        return fixed.copy(zoneHeightDp = zone).toLayout(zoneMin, header, rowHeight, space.labelScale, widths, scrolls)
    }

    /** The fixed parts after [step] degradations: 0 none, 1 keys and action bar, 2 margins and gaps too. */
    private fun fixedAt(step: Int, zone: Float): Fixed = Fixed(
        keyHeightDp = if (step >= 1) KEY_HEIGHT_MIN else KEY_HEIGHT,
        actionBarMinHeightDp = if (step >= 1) ACTION_BAR_HEIGHT_MIN else ACTION_BAR_HEIGHT,
        topMarginDp = if (step >= 2) TOP_MARGIN_MIN else TOP_MARGIN,
        bottomMarginDp = if (step >= 2) BOTTOM_MARGIN_MIN else BOTTOM_MARGIN,
        gapDp = if (step >= 2) GAP_MIN else GAP,
        zoneHeightDp = zone,
    )

    private fun columnWidths(space: CricketSpace, boardWidth: Float): Widths {
        val label = labelColumnWidth(space.labelScale)
        return Widths(label, (boardWidth - label) / space.playerCount.coerceAtLeast(1), boardWidth)
    }

    private data class Widths(val label: Float, val player: Float, val board: Float)

    private data class Fixed(
        val keyHeightDp: Float,
        val actionBarMinHeightDp: Float,
        val topMarginDp: Float,
        val bottomMarginDp: Float,
        val gapDp: Float,
        val zoneHeightDp: Float,
    ) {
        private val keypadHeight: Float
            get() = KEYPAD_ROWS * keyHeightDp + (KEYPAD_ROWS - 1) * gapDp

        /** Everything but the header and the board, in portrait: zone, keypad, action bar, margins and four gaps. */
        fun portraitFixedHeight(): Float =
            zoneHeightDp + keypadHeight + actionBarMinHeightDp + topMarginDp + bottomMarginDp + GAPS_PORTRAIT * gapDp

        /** The right column in landscape, zone excluded: keypad, action bar, margins and two gaps. */
        fun landscapeFixedHeight(): Float =
            keypadHeight + actionBarMinHeightDp + topMarginDp + bottomMarginDp + GAPS_LANDSCAPE_RIGHT * gapDp

        fun toLayout(
            zoneMin: Float,
            header: Float,
            rowHeight: Float,
            textScale: Float,
            widths: Widths,
            scrolls: Boolean,
        ): CricketLayout = CricketLayout(
            keyHeightDp = keyHeightDp,
            actionBarMinHeightDp = actionBarMinHeightDp,
            gapDp = gapDp,
            topMarginDp = topMarginDp,
            bottomMarginDp = bottomMarginDp,
            zoneHeightDp = zoneHeightDp,
            zoneMinHeightDp = zoneMin,
            headerHeightDp = header,
            rowHeightDp = rowHeight,
            columnHeaderHeightDp = columnHeaderHeight(textScale),
            labelColumnWidthDp = widths.label,
            playerColumnWidthDp = widths.player,
            boardWidthDp = widths.board,
            boardScrolls = scrolls,
        )
    }
}
