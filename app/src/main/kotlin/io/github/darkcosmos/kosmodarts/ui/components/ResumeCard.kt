package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.format.displayName
import io.github.darkcosmos.kosmodarts.ui.format.formatText
import io.github.darkcosmos.kosmodarts.ui.format.tileMatchText
import io.github.darkcosmos.kosmodarts.ui.game.MatchIndicator
import io.github.darkcosmos.kosmodarts.ui.home.HomeTags
import io.github.darkcosmos.kosmodarts.ui.home.ResumeSummary
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.PlayerPalette

private val STRIP = 4.dp
private val CARD_PADDING = 14.dp

/**
 * The "GAME IN PROGRESS" card of the home screen (design system, `ResumeCard`; R2, M18): the format line, one mini
 * tile per player in play order (color strip, name, score, "S n · M n" in a match), then the button "RESUME · <player>
 * TO THROW" (owner Q11 of issue #49). No ▸ marker nor accent outline (owner Q10). A long name wraps the button label,
 * and the button grows (U6).
 */
@Composable
fun ResumeCard(summary: ResumeSummary, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val colors = KosmoTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .clip(KosmoShapes.card)
            .background(colors.surface)
            .testTag(HomeTags.SUMMARY)
            .padding(CARD_PADDING),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.home_saved_game_title),
                style = KosmoTheme.typography.label,
                color = colors.accent,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Text(
                text = formatText(summary.startScore, summary.checkoutRule, summary.format),
                style = KosmoTheme.typography.label,
                color = colors.onBackgroundMuted,
                textAlign = TextAlign.End,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            summary.players.forEachIndexed { index, player -> MiniTile(player, index, Modifier.weight(1f)) }
        }
        KosmoButton(
            text = stringResource(R.string.home_resume, displayName(summary.highlightedPlayer)),
            onClick = onResume,
            minHeight = 56.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HomeTags.RESUME),
        )
    }
}

/** One player of the card, read as one TalkBack item ("ANN, 247, S0 · M1"). */
@Composable
private fun MiniTile(player: ResumeSummary.PlayerScore, index: Int, modifier: Modifier) {
    val colors = KosmoTheme.colors
    Column(
        modifier = modifier
            .clip(KosmoShapes.control)
            .background(colors.background)
            .testTag(HomeTags.player(index))
            .semantics(mergeDescendants = true) {},
    ) {
        Box(Modifier.fillMaxWidth().height(STRIP).background(PlayerPalette.colorOf(player.slot, colors)))
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = displayName(player.name),
                style = KosmoTheme.typography.playerNameTile,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = player.remaining.toString(),
                style = KosmoTheme.typography.tileScoreSmall,
                color = colors.onBackground,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag(HomeTags.score(index)),
            )
            // M18: the match score, as on the tiles (M14); nothing in a single game.
            player.legsWon?.let { legs ->
                Text(
                    text = tileMatchText(MatchIndicator(player.setsWon, legs)),
                    style = KosmoTheme.typography.label,
                    color = colors.onBackgroundMuted,
                    maxLines = 1,
                    modifier = Modifier.testTag(HomeTags.matchScore(index)),
                )
            }
        }
    }
}
