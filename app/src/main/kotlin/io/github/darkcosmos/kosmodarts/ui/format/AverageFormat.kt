package io.github.darkcosmos.kosmodarts.ui.format

import java.util.Locale

/** Three-dart average with two decimals, with the decimal separator of [locale] (rule A1: rounding is for display). */
fun formatAverage(average: Double, locale: Locale): String = String.format(locale, "%.2f", average)
