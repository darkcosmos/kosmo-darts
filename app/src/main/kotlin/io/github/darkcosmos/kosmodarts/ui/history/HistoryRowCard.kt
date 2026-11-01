package io.github.darkcosmos.kosmodarts.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.history.HistoryMatch
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.States
import java.time.ZoneId

private const val NAME_MAX_LINES = 2

/**
 * One match of the list (HS2): the end date, the mode and variant, the players in the match color (names wrap on two
 * lines, then end with an ellipsis), the winner by text and the legs or sets score. The whole row is one button for
 * TalkBack with one description (date, mode, players, winner, score); it is at least 48 dp high.
 */
@Composable
fun HistoryRowCard(match: HistoryMatch, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = KosmoTheme.colors
    val resources = LocalContext.current.resources
    val locale = LocalConfiguration.current.locales[0]
    val text = remember(match, locale) { match.toRowText(resources, locale, ZoneId.systemDefault()) }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(HistoryTags.row(match.sessionId))
            .clip(KosmoShapes.card)
            .background(colors.surface)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.history_row_action),
                onClick = onClick,
            )
            .sizeIn(minHeight = States.minTouchTarget)
            .clearAndSetSemantics { contentDescription = text.description }
            .padding(16.dp),
    ) {
        Text(text.date, style = KosmoTheme.typography.body, color = colors.onBackgroundMuted)
        Text(text.mode, style = KosmoTheme.typography.bodyLarge, color = colors.onBackground)
        text.players.forEach { player ->
            Text(
                text = player.name,
                style = KosmoTheme.typography.bodyLarge,
                color = Color(player.colorArgb),
                maxLines = NAME_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = text.winner,
                style = KosmoTheme.typography.body,
                color = colors.onBackground,
                modifier = Modifier.weight(1f, fill = false),
            )
            text.score?.let { Text(it, style = KosmoTheme.typography.body, color = colors.onBackgroundMuted) }
        }
    }
}
