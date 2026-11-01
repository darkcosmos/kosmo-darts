package io.github.darkcosmos.kosmodarts.ui.game

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.components.BarAction
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.Spacing

/**
 * The confirm button. While the match is saved in the history it is disabled and says so (AR3): « Enregistrement… »
 * and a description for a screen reader. The tap is also ignored by the reducer.
 */
@Composable
internal fun confirmAction(outcome: LegOutcome, archive: ArchiveStatus, onEvent: (AppEvent) -> Unit): BarAction =
    if (archive is ArchiveStatus.Saving) {
        BarAction(
            stringResource(R.string.archive_saving_label),
            GameTags.CONFIRM,
            isEnabled = false,
            description = stringResource(R.string.archive_saving_description),
        ) {}
    } else {
        BarAction(confirmLabel(outcome), GameTags.CONFIRM, isEnabled = true) { onEvent(AppEvent.FinishGameTapped) }
    }

/**
 * AR3: the short text above the buttons when the last write failed, read out when it appears (polite live region),
 * never shown by color only. Nothing while [archive] is not failed.
 */
@Composable
internal fun ArchiveFailedMessage(archive: ArchiveStatus, modifier: Modifier = Modifier) {
    if (archive != ArchiveStatus.Failed) return
    Text(
        text = stringResource(R.string.archive_failed_message),
        style = KosmoTheme.typography.body,
        color = KosmoTheme.colors.onBackground,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.s)
            .testTag(GameTags.ARCHIVE_FAILED)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * The height in dp the failure message takes when it wraps inside [widthDp], bottom padding included; 0 while
 * [archive] is not failed. Measured with the real text and density, so a layout that must reserve the room (the
 * Cricket landscape board) follows the text instead of a constant.
 */
@Composable
internal fun rememberArchiveMessageHeightDp(archive: ArchiveStatus, widthDp: Float): Float {
    if (archive != ArchiveStatus.Failed) return 0f
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val text = stringResource(R.string.archive_failed_message)
    val style = KosmoTheme.typography.body
    return with(density) {
        val constraints = Constraints(maxWidth = widthDp.dp.roundToPx())
        measurer.measure(text, style, constraints = constraints).size.height.toDp().value + Spacing.s.value
    }
}
