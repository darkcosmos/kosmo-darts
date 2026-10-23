package io.github.darkcosmos.kosmodarts.ui.layout

/**
 * Layout of the screens other than the game (home, setup, between legs, end, settings), in dp after the insets.
 *
 * @property horizontalMarginDp screen margin on both sides.
 * @property verticalMarginDp screen margin at the top and bottom.
 * @property contentMaxWidthDp content never gets wider than this (landscape, tablets).
 * @property isMainButtonPinned whether the main button(s) stay at the bottom of the screen while the rest scrolls;
 *   otherwise they scroll with the content, so that a short screen at a large font size never loses its content
 *   behind them.
 */
data class ScreenLayout(
    val horizontalMarginDp: Float,
    val verticalMarginDp: Float,
    val contentMaxWidthDp: Float,
    val isMainButtonPinned: Boolean,
)

/**
 * Pure policy of the other screens (plan of issue #49, section 4b, U1, U7): everything scrolls vertically, except the
 * main button(s), pinned at the bottom while the screen keeps enough height for the content above them. No Compose
 * type: plain numbers, tested on the JVM.
 */
object ScreenLayoutPolicy {

    const val HORIZONTAL_MARGIN = 12f
    const val VERTICAL_MARGIN = 16f
    const val CONTENT_MAX_WIDTH = 560f

    /** Two columns side by side never get wider than this together (landscape). */
    const val TWO_COLUMNS_MAX_WIDTH = 960f

    /** Two columns need at least this width; a narrower landscape window keeps one column. */
    private const val TWO_COLUMNS_MIN_WIDTH = 600f

    /**
     * Whether a screen that has two columns in landscape (between legs: the winner on the left, the scoreboard and
     * "Next leg" on the right; owner review of PR #57) shows them side by side, in a window of [widthDp] × [heightDp].
     */
    fun hasTwoColumns(widthDp: Float, heightDp: Float): Boolean = widthDp > heightDp && widthDp >= TWO_COLUMNS_MIN_WIDTH

    /** Height (dp at font scale 1) of one pinned button, its margin included. */
    private const val PINNED_BUTTON = 74f

    /** The content above pinned buttons keeps at least this height, else the buttons scroll with it. */
    private const val MIN_CONTENT_HEIGHT = 240f

    /**
     * @param buttonCount number of stacked main buttons at the bottom (1 or 2).
     * @param alwaysPinned the setup keeps "Start" pinned above the keyboard whatever the height (U7).
     */
    fun of(
        heightDp: Float,
        fontScale: Float,
        buttonCount: Int = 1,
        alwaysPinned: Boolean = false,
    ): ScreenLayout {
        val buttons = buttonCount * PINNED_BUTTON * maxOf(1f, fontScale)
        return ScreenLayout(
            horizontalMarginDp = HORIZONTAL_MARGIN,
            verticalMarginDp = VERTICAL_MARGIN,
            contentMaxWidthDp = CONTENT_MAX_WIDTH,
            isMainButtonPinned = alwaysPinned || heightDp - buttons >= MIN_CONTENT_HEIGHT,
        )
    }
}
