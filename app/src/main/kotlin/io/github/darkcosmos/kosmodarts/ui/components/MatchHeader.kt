package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme

/**
 * The top line of the game screen (design system, `MatchHeader`): the mode and the finish ("501 · DOUBLE OUT") at the
 * start, the position in the match ("SET 2 · LEG 3", M14, M19) at the end, nothing there in a single game. Both in
 * `label`, `onBackgroundMuted`; they wrap rather than being cut at a large font size.
 */
@Composable
fun MatchHeader(mode: String, position: String?, modifier: Modifier = Modifier) {
    val style = KosmoTheme.typography.label
    val color = KosmoTheme.colors.onBackgroundMuted
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = mode, style = style, color = color, modifier = Modifier.weight(1f))
        position?.let {
            Text(
                text = it,
                style = style,
                color = color,
                textAlign = TextAlign.End,
                modifier = Modifier.testTag(GameTags.HEADER_POSITION),
            )
        }
    }
}
