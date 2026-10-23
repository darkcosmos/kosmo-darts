package io.github.darkcosmos.kosmodarts.ui.format

import java.util.Locale

/**
 * Marks per round with one decimal, with the decimal separator of [locale], rounded half up (docs/rules/cricket.md,
 * CR13: the engine gives the exact value, rounding is for display), or [none] (the dash) when no dart was thrown.
 */
fun formatMpr(mpr: Double?, locale: Locale, none: String): String =
    if (mpr == null) none else String.format(locale, "%.1f", mpr)
