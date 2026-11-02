package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.ui.layout.GameLayoutPolicy
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Step between two candidate text scales (a 2.0 font size gives 20 candidates down to 1.0). */
private const val SCALE_STEP = 0.05f

/** Vertical padding of one S/M chip of the name row, in dp. */
private const val CHIP_VERTICAL_PADDING = 2

/**
 * Issue #69, M15, design system 6.3: the text scale of the name row and of the checkout line while a checkout waits.
 * Returns the largest candidate scale, from [fontScale] down to 1 (D2: never below the default size), for which
 * `fixed + textHeightAt(scale) <= available`, else 1. [textHeightAt] gives the total height of those texts at a scale
 * and is not linear (Android's non-linear font scaling, wrapping), so the candidates are measured one by one. A
 * [fontScale] of 1 or less is returned as it is.
 */
internal fun checkoutTextScale(
    available: Float,
    fixed: Float,
    fontScale: Float,
    textHeightAt: (Float) -> Float,
): Float {
    if (fontScale <= 1f) return fontScale
    val steps = ceil((fontScale - 1f) / SCALE_STEP).toInt()
    return (0..steps).asSequence()
        .map { step -> maxOf(1f, fontScale - step * SCALE_STEP) }
        .firstOrNull { scale -> fixed + textHeightAt(scale) <= available }
        ?: 1f
}

/**
 * The text scale for the name row and the checkout line of a zone of [constraints] while a checkout waits: the
 * zone's frame, the CHECKOUT chip at its least height and the dart slots are fixed, the two texts take the rest. A
 * null [lineText] (no checkout pending) or a zone of unbounded height keeps the user's font scale.
 */
@Composable
internal fun rememberCheckoutTextScale(constraints: Constraints, lineText: String?, hasChips: Boolean): Float {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val typography = KosmoTheme.typography
    return remember(constraints, lineText, hasChips, density, typography) {
        if (lineText == null || !constraints.hasBoundedHeight) return@remember density.fontScale
        val fixedDp = 2 * ZONE_PADDING.value + 2 * INNER_GAP.value + DART_SLOT_HEIGHT.value +
            GameLayoutPolicy.CHECKOUT_BANNER_MIN + INNER_GAP.value
        val lineWidth = (constraints.maxWidth - with(density) { (2 * ZONE_PADDING.value).dp.toPx() }).roundToInt()
        val fixed = with(density) { fixedDp.dp.toPx() }
        checkoutTextScale(constraints.maxHeight.toFloat(), fixed, density.fontScale) { scale ->
            val at = Density(density.density, scale)
            val nameRow = maxOf(
                measurer.measure("Ag", typography.playerNameZone, maxLines = 1, density = at).size.height,
                measurer.measure("Ag", typography.labelLarge, maxLines = 1, density = at).size.height +
                    if (hasChips) with(at) { (2 * CHIP_VERTICAL_PADDING).dp.roundToPx() } else 0,
            )
            val line = measurer.measure(
                lineText,
                typography.labelLarge,
                constraints = Constraints(maxWidth = lineWidth.coerceAtLeast(0)),
                density = at,
            ).size.height
            (nameRow + line).toFloat()
        }
    }
}
