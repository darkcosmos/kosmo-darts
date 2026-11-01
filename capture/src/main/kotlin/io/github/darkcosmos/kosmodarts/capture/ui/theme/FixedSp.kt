package io.github.darkcosmos.kosmodarts.capture.ui.theme

// Copied from :app (issue #41, decision Q11); not kept in sync.

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * A text size that draws [dp] density-independent pixels whatever the Android font size setting (design system,
 * section 3, "fixe"): [dp] converted to sp with this density, so the conversion back to pixels cancels the font scale,
 * the non-linear scaling of Android 14 included. Used by the styles that are already larger than the setting would
 * make them: the score, the keypad, the dart slots, the visit box and the tile scores.
 */
fun Density.fixedSp(dp: Float): TextUnit = dp.dp.toSp()
