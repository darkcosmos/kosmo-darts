package io.github.darkcosmos.kosmodarts.ui.board

import androidx.compose.ui.geometry.Offset
import io.github.darkcosmos.kosmodarts.engine.board.BoardGeometry
import io.github.darkcosmos.kosmodarts.engine.board.BoardPoint
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone

/**
 * Half the side of the square of the component, in board millimetres: the double ring ends at
 * [BoardGeometry.DOUBLE_OUTER_MM], the rest is the ring of the numbers.
 */
internal const val HALF_SIDE_MM = 205.0

/** Pixel to millimetre conversion of a square of [sizePx] pixels (rule BC4): y grows upwards on the board. */
internal fun boardPointAt(offset: Offset, sizePx: Float): BoardPoint {
    val centre = sizePx / 2.0
    val mmPerPx = HALF_SIDE_MM / centre
    return BoardPoint((offset.x - centre) * mmPerPx, (centre - offset.y) * mmPerPx)
}

/** The zone under a tap at [offset] (rule BC5), resolved by the engine; `null` outside the double ring. */
internal fun zoneAtPixel(offset: Offset, sizePx: Float): BoardZone? =
    if (sizePx <= 0f) null else BoardGeometry.zoneAt(boardPointAt(offset, sizePx))
