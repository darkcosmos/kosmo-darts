package io.github.darkcosmos.kosmodarts.ui.format

import java.util.Locale

private const val PERCENT = 100

/**
 * HM5, HM6: a ratio (0 to 1, as the engine gives it) as a percentage with at most one decimal, "4.7 %" or "30 %"
 * ("4,7 %" in French). The ratio is multiplied by 100 here and nowhere else.
 */
fun formatPercent(ratio: Double, locale: Locale): String {
    val tenths = Math.round(ratio * PERCENT * 10) / 10.0
    val pattern = if (tenths % 1.0 == 0.0) "%.0f %%" else "%.1f %%"
    return String.format(locale, pattern, tenths)
}

/** A ratio (0 to 1) as a whole percentage, "45 %". */
fun formatWholePercent(ratio: Double, locale: Locale): String = String.format(locale, "%.0f %%", ratio * PERCENT)
