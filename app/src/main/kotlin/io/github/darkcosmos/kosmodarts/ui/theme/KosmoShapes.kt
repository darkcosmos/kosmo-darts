package io.github.darkcosmos.kosmodarts.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Shapes of the design system (section 4): 4 for controls, keys, tiles and rows; 8 for the zone and cards. No pill. */
object KosmoShapes {
    val control: RoundedCornerShape = RoundedCornerShape(4.dp)
    val card: RoundedCornerShape = RoundedCornerShape(8.dp)
}

/** Spacings of the design system (section 4): 4 to 16; screen margins 12 horizontal, 16 vertical. */
object Spacing {
    val xxs = 4.dp
    val xs = 6.dp
    val s = 8.dp
    val m = 10.dp
    val l = 12.dp
    val xl = 14.dp
    val xxl = 16.dp
    val screenHorizontal = 12.dp
    val screenVertical = 16.dp
}

/** States of the design system (section 4). */
object States {
    /** Opacity of a disabled control. */
    const val DISABLED_ALPHA: Float = 0.40f

    /** Opacity of the whole keypad while it is locked (BUST, CHECKOUT, a turn waiting). */
    const val LOCKED_KEYPAD_ALPHA: Float = 0.35f

    /** Width of the `accent` border of focus and selection. */
    val selectionBorder = 2.dp

    /** Smallest touch target. */
    val minTouchTarget = 48.dp
}
