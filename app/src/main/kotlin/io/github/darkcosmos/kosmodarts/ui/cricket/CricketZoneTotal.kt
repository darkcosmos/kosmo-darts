package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTypography
import io.github.darkcosmos.kosmodarts.ui.theme.fixedSp

/** The widest total, in tabular figures: the size is fitted on it so it does not change with the digits shown. */
internal const val ZONE_TOTAL_REFERENCE = "8888"

/** The widest MPR: one digit, the decimal separator and one decimal. */
internal const val ZONE_MPR_REFERENCE = "8,8"

/**
 * The big number of the portrait zone (CR22): the current player's total, or in a solo game the MPR. Same method as the
 * X01 score area (the X01 file stays untouched): the size is the fixed `scoreHuge` size, reduced so that [reference]
 * fits the width and the height left, never clipped, whatever the digits shown. [description] is what TalkBack reads.
 */
@Composable
internal fun CricketZoneTotal(
    text: String,
    reference: String,
    description: String,
    tag: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(contentAlignment = Alignment.Center, modifier = modifier) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val baseStyle = KosmoTheme.typography.scoreHuge
        val size = remember(constraints, baseStyle, density, reference) {
            val measured = measurer.measure(
                reference,
                baseStyle.copy(fontSize = density.fixedSp(KosmoTypography.SCORE_PORTRAIT)),
                softWrap = false,
                maxLines = 1,
            ).size
            val fit = minOf(
                1f,
                constraints.maxWidth.toFloat() / measured.width.coerceAtLeast(1),
                // One pixel less: the fitted line rounds to whole pixels and must not overflow the area.
                (constraints.maxHeight - 1).coerceAtLeast(1).toFloat() / measured.height.coerceAtLeast(1),
            )
            KosmoTypography.SCORE_PORTRAIT * fit
        }
        Text(
            text = text,
            style = baseStyle.copy(fontSize = density.fixedSp(size)),
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag)
                .semantics { contentDescription = description },
        )
    }
}
