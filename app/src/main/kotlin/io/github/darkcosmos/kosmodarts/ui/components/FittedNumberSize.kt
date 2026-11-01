package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import io.github.darkcosmos.kosmodarts.ui.theme.fixedSp

/**
 * The ratio, at most 1, by which a number measured at [referenceWidth] x [referenceHeight] pixels must shrink to fit
 * [maxWidth] x [maxHeight]. A zero reference side counts as one pixel, a zero area height as one pixel.
 */
internal fun fitScale(maxWidth: Int, maxHeight: Int, referenceWidth: Int, referenceHeight: Int): Float = minOf(
    1f,
    maxWidth.toFloat() / referenceWidth.coerceAtLeast(1),
    // One pixel less: the fitted line rounds to whole pixels and must not overflow the area.
    (maxHeight - 1).coerceAtLeast(1).toFloat() / referenceHeight.coerceAtLeast(1),
)

/**
 * The font size in dp for a big number: [baseSize], reduced so that the widest number to show, [reference], fits
 * [constraints]. Fitted on [reference] and not on the shown text, so the size does not jump with the digits.
 */
@Composable
internal fun rememberFittedNumberSize(
    reference: String,
    style: TextStyle,
    baseSize: Float,
    constraints: Constraints,
): Float {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    return remember(constraints, style, baseSize, density, reference) {
        val measured = measurer.measure(
            reference,
            style.copy(fontSize = density.fixedSp(baseSize)),
            softWrap = false,
            maxLines = 1,
        ).size
        baseSize * fitScale(constraints.maxWidth, constraints.maxHeight, measured.width, measured.height)
    }
}
