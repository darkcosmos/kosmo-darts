package io.github.darkcosmos.kosmodarts.ui.layout

/** How the other players' tiles are drawn (design system, `PlayerTile`; docs/rules/match.md, M6). */
enum class TileVariant {
    /** Portrait, 2 per line (up to 3 players): name and match line on the first line, score and box on the second. */
    WIDE,

    /** Portrait, 3 per line (4 players): name, then score and box, then the match line. */
    NARROW,

    /**
     * Landscape, 2 and 3 players: laid out as [COMPACT_TWO_LINES] (U19: on one line the name lost its characters), with
     * the larger tile score.
     */
    COMPACT,

    /**
     * Landscape, 4 players (owner Q9, U3): the name (two lines at most) and the match line on the first line, score and
     * box on the second (owner review of PR #57: lower tiles).
     */
    COMPACT_TWO_LINES,
}

/**
 * What the game screen needs to know about its space, in dp after the insets (docs/plans/49-design-system.md, 4b).
 *
 * @property textScale how much the font size setting enlarges text that follows it: the dp height of a 24 sp text
 *   divided by 24 (1.0 to 2.0; Android 14 enlarges large text less than small text, so this is less than the setting
 *   at large sizes).
 * @property isMatch whether the match has legs or sets: the tiles then have a match line (M14).
 * @property isCheckoutPending whether the zone shows the CHECKOUT banner instead of the score and the route (M15): the
 *   zone then needs less height, and gives the rest back (U10). The other sizes do not change, so nothing else moves.
 */
data class GameSpace(
    val widthDp: Float,
    val heightDp: Float,
    val textScale: Float,
    val playerCount: Int,
    val isMatch: Boolean,
    val isCheckoutPending: Boolean = false,
) {
    val isLandscape: Boolean
        get() = widthDp > heightDp
}

/**
 * The sizes of the game screen, in dp. Composables convert them to `Dp` and only apply them.
 *
 * @property step the last degradation step applied: 0 for none (the mockup values), then 1 to 4 (plan, section 4b).
 * @property isScrolling whether the screen (portrait) or each column (landscape) is expected to scroll: below the score
 *   floor. The screen measures its parts and scrolls whenever they do not fit, so this is the estimate only.
 * @property zoneMinHeightDp the zone never gets less height than this: its fixed parts with the score at its floor, or
 *   with the CHECKOUT banner at its least height while a checkout waits (no score, no route).
 * @property leftColumnWidthDp landscape only: the width of the zone and tiles column.
 */
data class GameLayout(
    val scoreSizeDp: Float,
    val keyHeightDp: Float,
    val actionBarMinHeightDp: Float,
    val verticalMarginDp: Float,
    val gapDp: Float,
    val tileVariant: TileVariant,
    val tilesPerRow: Int,
    val isScrolling: Boolean,
    val zoneMinHeightDp: Float,
    val leftColumnWidthDp: Float,
    val step: Int,
)

/**
 * Sizes of the game screen as a pure function of its space (plan of issue #49, section 4b; U1). Fixed parts are
 * estimated first from the line heights of the text styles (those that follow the font size setting are multiplied by
 * [GameSpace.textScale]) and the zone gets the rest. When the score does not fit, the steps apply in
 * order, each one only if the previous one is not enough:
 * 1. keypad keys from 54 down to 48 dp, action bar from 58 down to 48 dp (never below 48: touch targets);
 * 2. vertical screen margin from 16 down to 8 dp, gaps from 6 down to 4 dp;
 * 3. the score shrinks to the free height, never below its floor (96 portrait, 72 landscape);
 * 4. below the floor, the screen (portrait) or the column (landscape) scrolls: nothing is ever clipped. The action bar
 *    is never part of the scrolling area: it stays pinned at the bottom, so "Undo" and the main action are always on
 *    screen (U10).
 *
 * The steps are chosen for the zone with its score, in every zone mode, so the keypad never changes size when a
 * checkout waits; only the zone's least height is lower then ([GameSpace.isCheckoutPending]).
 *
 * No Compose type here: inputs and outputs are plain numbers, so the policy is tested on the JVM.
 */
object GameLayoutPolicy {

    const val KEY_HEIGHT = 54f
    const val KEY_HEIGHT_MIN = 48f
    const val ACTION_BAR_HEIGHT = 58f
    const val ACTION_BAR_HEIGHT_MIN = 48f
    const val MARGIN = 16f
    const val MARGIN_MIN = 8f
    const val GAP = 6f
    const val GAP_MIN = 4f
    const val SCORE_FLOOR_PORTRAIT = 96f
    const val SCORE_FLOOR_LANDSCAPE = 72f
    const val SCORE_PORTRAIT = 220f
    const val SCORE_PORTRAIT_FOUR_PLAYERS = 204f
    const val SCORE_LANDSCAPE = 160f
    const val LEFT_COLUMN_WIDTH = 480f

    /**
     * Landscape: share of the zone's width given to the route, right of the score, so that the route stays on one line
     * (owner review of PR #57); the score is fitted to the rest.
     */
    const val LANDSCAPE_ROUTE_SHARE = 0.6f

    /** Landscape tiles: vertical padding, lower than the portrait one, so the tiles fit a phone's height. */
    const val TILE_PADDING_LANDSCAPE_VERTICAL = 4f

    /** Share of the width the left column may take in landscape when the screen is narrower than the mockup. */
    private const val LEFT_COLUMN_MAX_SHARE = 0.55f
    private const val KEYPAD_ROWS = 5
    private const val FOUR_PLAYERS = 4

    // Heights (dp at font scale 1) of the parts that follow the font scale, from the line heights of the styles.
    private const val HEADER_LINE = 17f
    private const val ZONE_NAME_LINE = 34f
    private const val ROUTE_LINE = 34f
    private const val TILE_NAME_LINE = 22f
    private const val TILE_MATCH_LINE = 17f
    private const val BUTTON_LINE = 24f
    private const val LABEL_LINE = 22f

    // Fixed heights (dp) of the parts that ignore the font scale.
    private const val ZONE_PADDING = 12f
    private const val ZONE_INNER_GAP = 8f

    /** Gaps between the four parts of the portrait zone: name, score, route, darts. */
    private const val PORTRAIT_ZONE_GAPS = 3
    private const val ROUTE_PADDING = 8f
    private const val DART_SLOTS = 46f
    private const val TILE_STRIP = 4f
    private const val TILE_PADDING = 8f
    private const val TILE_SCORE_LINE = 38f
    private const val BUTTON_PADDING = 12f

    /** From this text scale on, the action bar labels are counted on two lines (U6). */
    private const val TWO_LINE_LABELS_FROM = 1.3f

    /** Margin on the estimated least height of the zone. */
    private const val SAFETY = 1.1f

    /** Least height of the CHECKOUT banner chip: its text shrinks to fit (design system 6.3). */
    private const val CHECKOUT_BANNER_MIN = 40f

    /** The line under the banner, "141 IN 3 DARTS", in the `labelLarge` style. */
    private const val CHECKOUT_LINE = 22f

    fun of(space: GameSpace): GameLayout = if (space.isLandscape) landscape(space) else portrait(space)

    private data class Shape(val variant: TileVariant, val tilesPerRow: Int, val leftWidth: Float, val zoneMin: Float)

    private data class Choice(val keyHeight: Float, val actionBar: Float, val margin: Float, val gap: Float)

    private val STEPS = listOf(
        Choice(KEY_HEIGHT, ACTION_BAR_HEIGHT, MARGIN, GAP),
        Choice(KEY_HEIGHT_MIN, ACTION_BAR_HEIGHT_MIN, MARGIN, GAP),
        Choice(KEY_HEIGHT_MIN, ACTION_BAR_HEIGHT_MIN, MARGIN_MIN, GAP_MIN),
    )

    private fun portrait(space: GameSpace): GameLayout {
        val maxScore = if (space.playerCount >= FOUR_PLAYERS) SCORE_PORTRAIT_FOUR_PLAYERS else SCORE_PORTRAIT
        val tilesPerRow = if (space.playerCount >= FOUR_PLAYERS) 3 else 2
        val variant = if (space.playerCount >= FOUR_PLAYERS) TileVariant.NARROW else TileVariant.WIDE
        val tileRows = (space.playerCount - 1 + tilesPerRow - 1) / tilesPerRow
        // The least height of the zone, kept on the safe side because the zone is never squeezed below it: the route
        // may wrap on two lines at large text sizes, and a margin covers the rounding of the measured lines.
        val zoneMin = if (space.isCheckoutPending) {
            checkoutZoneMinHeight(space)
        } else {
            zoneWithoutScore(space, labelLines(space)) * SAFETY + SCORE_FLOOR_PORTRAIT
        }
        return decide(space, maxScore, SCORE_FLOOR_PORTRAIT, Shape(variant, tilesPerRow, 0f, zoneMin)) { choice ->
            val tiles = if (tileRows == 0) 0f else tileRows * tileHeight(variant, space) + (tileRows - 1) * choice.gap
            val sections = if (tileRows == 0) 3 else 4
            2 * choice.margin + HEADER_LINE * space.textScale + sections * choice.gap + zoneWithoutScore(space, 1) +
                tiles + keypadHeight(choice) + actionBarHeight(choice, space)
        }
    }

    /**
     * Landscape: the left column holds the header, the zone (score with the one-line route on its right) and the tiles;
     * the right column holds the keypad and the action bar. Each column is checked on its own; the steps are shared.
     */
    private fun landscape(space: GameSpace): GameLayout {
        val leftWidth = minOf(LEFT_COLUMN_WIDTH, space.widthDp * LEFT_COLUMN_MAX_SHARE)
        val variant = if (space.playerCount >= FOUR_PLAYERS) TileVariant.COMPACT_TWO_LINES else TileVariant.COMPACT
        val tilesPerRow = maxOf(1, space.playerCount - 1)
        val hasTiles = space.playerCount > 1
        // The route stands on one line right of the score (owner review of PR #57): label chip or darts, the higher.
        val routeLine = maxOf(
            LABEL_LINE * space.textScale + 2 * ROUTE_PADDING,
            ROUTE_LINE * space.textScale + ROUTE_PADDING,
        )
        val scoreZoneMin = zoneFrame(space) * SAFETY + maxOf(SCORE_FLOOR_LANDSCAPE, routeLine * SAFETY)
        // With the one-line route the score zone is low: a waiting checkout never asks for more, its banner shrinks.
        val zoneMin = if (space.isCheckoutPending) minOf(checkoutZoneMinHeight(space), scoreZoneMin) else scoreZoneMin
        val shape = Shape(variant, tilesPerRow, leftWidth, zoneMin)
        return decide(space, SCORE_LANDSCAPE, SCORE_FLOOR_LANDSCAPE, shape) { choice ->
            val tiles = if (hasTiles) tileHeight(variant, space) + choice.gap else 0f
            val left = 2 * choice.margin + HEADER_LINE * space.textScale + choice.gap +
                2 * ZONE_PADDING + ZONE_NAME_LINE * space.textScale + 2 * ZONE_INNER_GAP + DART_SLOTS + tiles
            val right = 2 * choice.margin + keypadHeight(choice) + choice.gap + actionBarHeight(choice, space)
            // The right column has no score: its overflow can only be absorbed by scrolling, never by the score.
            if (right > space.heightDp) Float.POSITIVE_INFINITY else left
        }
    }

    /**
     * Applies the steps in order. [fixedHeight] gives every height but the score's for a choice of sizes; the score
     * gets the rest of [GameSpace.heightDp].
     */
    private fun decide(
        space: GameSpace,
        maxScore: Float,
        floor: Float,
        shape: Shape,
        fixedHeight: (Choice) -> Float,
    ): GameLayout {
        fun layout(choice: Choice, score: Float, scrolling: Boolean, step: Int) = GameLayout(
            scoreSizeDp = score,
            keyHeightDp = choice.keyHeight,
            actionBarMinHeightDp = choice.actionBar,
            verticalMarginDp = choice.margin,
            gapDp = choice.gap,
            tileVariant = shape.variant,
            tilesPerRow = shape.tilesPerRow,
            isScrolling = scrolling,
            zoneMinHeightDp = shape.zoneMin,
            leftColumnWidthDp = shape.leftWidth,
            step = step,
        )
        STEPS.forEachIndexed { step, choice ->
            if (space.heightDp - fixedHeight(choice) >= maxScore) return layout(choice, maxScore, false, step)
        }
        val smallest = STEPS.last()
        val free = space.heightDp - fixedHeight(smallest)
        return if (free >= floor) {
            layout(smallest, free, scrolling = false, step = STEPS.size)
        } else {
            layout(smallest, floor, scrolling = true, step = STEPS.size + 1)
        }
    }

    /**
     * The least height of the zone while a checkout waits (U10): name, the banner at its least height with its line,
     * and the dart slots; no score and no route area (CS8), so the zone gives that height back to the screen.
     */
    private fun checkoutZoneMinHeight(space: GameSpace): Float =
        zoneFrame(space) * SAFETY + CHECKOUT_BANNER_MIN + ZONE_INNER_GAP +
            labelLines(space) * CHECKOUT_LINE * space.textScale

    /** Padding, name line, the two gaps around the middle part, and the dart slots: present in every zone mode. */
    private fun zoneFrame(space: GameSpace): Float =
        2 * ZONE_PADDING + ZONE_NAME_LINE * space.textScale + 2 * ZONE_INNER_GAP + DART_SLOTS

    /** Labels that may wrap (route, action bar, checkout line) are counted on two lines at large text sizes (U6). */
    private fun labelLines(space: GameSpace): Int = if (space.textScale >= TWO_LINE_LABELS_FROM) 2 else 1

    /** The portrait zone but its score: frame, the gap under the score, and the route area on [routeLines] lines. */
    private fun zoneWithoutScore(space: GameSpace, routeLines: Int): Float = zoneFrame(space) +
        (PORTRAIT_ZONE_GAPS - 2) * ZONE_INNER_GAP + routeLines * ROUTE_LINE * space.textScale + 2 * ROUTE_PADDING

    /** One tile: its strip, padding, name line, score line, and the match line when it has its own line. */
    private fun tileHeight(variant: TileVariant, space: GameSpace): Float {
        val name = TILE_NAME_LINE * space.textScale
        val matchLine = if (space.isMatch) TILE_MATCH_LINE * space.textScale else 0f
        val lines = when (variant) {
            TileVariant.WIDE -> maxOf(name, matchLine) + TILE_SCORE_LINE
            // The names of these two variants may take two lines at large text sizes (U3, T8).
            TileVariant.NARROW -> name * labelLines(space) + TILE_SCORE_LINE + matchLine
            TileVariant.COMPACT, TileVariant.COMPACT_TWO_LINES ->
                maxOf(name * labelLines(space), matchLine) + TILE_SCORE_LINE
        }
        return when (variant) {
            // The strip stands at the start of a landscape tile, so it adds no height.
            TileVariant.COMPACT, TileVariant.COMPACT_TWO_LINES -> 2 * TILE_PADDING_LANDSCAPE_VERTICAL + lines
            TileVariant.WIDE, TileVariant.NARROW -> TILE_STRIP + 2 * TILE_PADDING + lines
        }
    }

    private fun keypadHeight(choice: Choice): Float = KEYPAD_ROWS * choice.keyHeight + (KEYPAD_ROWS - 1) * choice.gap

    private fun actionBarHeight(choice: Choice, space: GameSpace): Float {
        return maxOf(choice.actionBar, labelLines(space) * BUTTON_LINE * space.textScale + 2 * BUTTON_PADDING)
    }
}
