package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.ui.format.displayName
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.PlayerPalette

/**
 * The winner's zone of the between-legs and end screens (mockups 08, 09): in the winner's color, an `accent` chip
 * ("SET 2 · LEG 3 WON", "MATCH WINNER"), the winner's name in capitals (shrunk to fit, never cut), and an optional
 * line under it (the winning checkout).
 */
@Composable
fun WinnerZone(
    chip: String,
    name: String,
    slot: ColorSlot,
    tags: WinnerZoneTags,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val colors = KosmoTheme.colors
    val typography = KosmoTheme.typography
    val onColor = PlayerPalette.onColorOf(slot, colors)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .clip(KosmoShapes.card)
            .background(PlayerPalette.colorOf(slot, colors))
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        Text(
            text = chip,
            style = typography.labelLarge,
            color = colors.onAccent,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(KosmoShapes.control)
                .background(colors.accent)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .testTag(tags.chip),
        )
        Text(
            text = displayName(name),
            style = typography.winnerName,
            color = onColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(maxFontSize = typography.winnerName.fontSize),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tags.name)
                .semantics { heading() },
        )
        detail?.let {
            Text(
                text = it,
                style = typography.labelLarge,
                color = onColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(tags.detail),
            )
        }
    }
}
