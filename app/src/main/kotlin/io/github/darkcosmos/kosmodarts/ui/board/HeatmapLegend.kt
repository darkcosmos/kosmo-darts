package io.github.darkcosmos.kosmodarts.ui.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import java.util.Locale

private const val PERCENT = 100
private val SWATCH_WIDTH = 28.dp
private val BAR_HEIGHT = 18.dp
private val BAR_SHAPE = RoundedCornerShape(3.dp)

/**
 * HM5: a legend value as a percentage with at most one decimal, "4.7 %" or "30 %" ("4,7 %" in French): the upper end of
 * the scale is the share of the most hit zone ([share] from 0 to 1).
 */
fun heatmapPercentText(share: Double, locale: Locale): String {
    val tenths = Math.round(share * PERCENT * 10) / 10.0
    val pattern = if (tenths % 1.0 == 0.0) "%.0f %%" else "%.1f %%"
    return String.format(locale, pattern, tenths)
}

/**
 * HM5: the legend of the relative scale ([HeatmapScale]): the neutral swatch "0 dart", the gradient bar from "> 0 %" to
 * "max = x %" (the share of the most hit zone, [maxShare] from 0 to 1), and the note on how a value is computed. The
 * meaning never relies on colour alone: the numbers are written, and TalkBack reads one description of the scale.
 */
@Composable
fun HeatmapLegend(maxShare: Double, modifier: Modifier = Modifier) {
    val colors = KosmoTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val maxText = heatmapPercentText(maxShare, locale)
    val description = stringResource(R.string.heatmap_legend_description, maxText)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(HeatmapLegendTags.LEGEND)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.heatmap_legend_title),
                color = colors.onBackground,
                style = KosmoTheme.typography.label,
            )
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NeutralSwatch()
                GradientBar(maxText, Modifier.weight(1f))
            }
            Text(
                stringResource(R.string.heatmap_legend_note),
                color = colors.onBackgroundMuted,
                style = KosmoTheme.typography.body,
            )
        }
    }
}

@Composable
private fun NeutralSwatch() {
    Column(horizontalAlignment = Alignment.Start) {
        Box(
            Modifier
                .width(SWATCH_WIDTH)
                .height(BAR_HEIGHT)
                .testTag(HeatmapLegendTags.NEUTRAL)
                .background(HeatmapScale.NEUTRAL, BAR_SHAPE)
                .border(BorderStroke(1.dp, HeatmapScale.NEUTRAL_FRAME), BAR_SHAPE),
        )
        Text(
            stringResource(R.string.heatmap_legend_zero),
            color = KosmoTheme.colors.onBackgroundMuted,
            style = KosmoTheme.typography.body,
        )
    }
}

@Composable
private fun GradientBar(maxText: String, modifier: Modifier = Modifier) {
    val colors = KosmoTheme.colors
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .testTag(HeatmapLegendTags.BAR)
                .background(Brush.horizontalGradient(HeatmapScale.STOPS), BAR_SHAPE),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.heatmap_legend_low),
                color = colors.onBackground,
                style = KosmoTheme.typography.body,
            )
            Text(
                stringResource(R.string.heatmap_legend_max, maxText),
                color = colors.onBackground,
                style = KosmoTheme.typography.body,
                textAlign = TextAlign.End,
                modifier = Modifier.testTag(HeatmapLegendTags.MAX),
            )
        }
    }
}

/** Test tags of [HeatmapLegend]. */
object HeatmapLegendTags {
    const val LEGEND = "heatmap_legend"
    const val NEUTRAL = "heatmap_legend_neutral"
    const val BAR = "heatmap_legend_bar"
    const val MAX = "heatmap_legend_max"
}
