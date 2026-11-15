package io.github.darkcosmos.kosmodarts.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States

/**
 * HM6: the fixed band under the board. It only shows the texts it is given (no logic, no popup): the [title], the name
 * of the tapped zone or the "Touch a zone" text, and its [detail] (darts and percentage), absent when nothing is
 * selected. Self-contained so that a screen can leave it out with one constant (D11). TalkBack announces a change.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZoneBand(title: String, detail: String?, modifier: Modifier = Modifier) {
    val colors = KosmoTheme.colors
    FlowRow(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(KosmoShapes.control)
            .background(colors.surface)
            .heightIn(min = States.minTouchTarget)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag(ZoneBandTags.BAND),
    ) {
        Text(
            text = title,
            style = KosmoTheme.typography.bodyLarge,
            color = if (detail == null) colors.onBackgroundMuted else colors.onBackground,
            modifier = Modifier
                .padding(end = 12.dp)
                .testTag(ZoneBandTags.TITLE),
        )
        detail?.let {
            Text(
                text = it,
                style = KosmoTheme.typography.bodyLarge,
                color = colors.onBackground,
                modifier = Modifier.testTag(ZoneBandTags.DETAIL),
            )
        }
    }
}

/** Test tags of [ZoneBand]. */
object ZoneBandTags {
    const val BAND = "zone_band"
    const val TITLE = "zone_band_title"
    const val DETAIL = "zone_band_detail"
}
