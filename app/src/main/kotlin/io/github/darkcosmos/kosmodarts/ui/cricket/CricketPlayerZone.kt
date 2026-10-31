package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.ui.components.Chips
import io.github.darkcosmos.kosmodarts.ui.components.DartSlots
import io.github.darkcosmos.kosmodarts.ui.components.ZoneStat
import io.github.darkcosmos.kosmodarts.ui.format.displayName
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.game.MatchIndicator
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.LocalAnimationSettings
import io.github.darkcosmos.kosmodarts.ui.theme.PlayerPalette
import io.github.darkcosmos.kosmodarts.ui.theme.zoneColorAnimationSpec

private val ZONE_PADDING_VERTICAL = 6.dp
private val ZONE_PADDING_HORIZONTAL = 10.dp
private val INNER_GAP = 8.dp
private val COMPACT_SLOT_HEIGHT = 36.dp
private val BANNER_PADDING = 8.dp

/** Everything the compact zone of the Cricket game screen shows (CR22, CR25). */
class CricketZoneContent(
    val index: Int,
    val name: String,
    val slot: ColorSlot,
    val stat: ZoneStat,
    val indicator: MatchIndicator?,
    val isVictory: Boolean,
    val score: String,
    val turnDarts: List<Dart>,
    val nextDartIndex: Int?,
)

/**
 * The current player's compact zone (CR22): in the player's color. Line 1: name (one line, ellipsis), the [ZoneStat]
 * (MPR), the S/M chips; line 2: the score in `tileScore` and the dart slots (the darts that give a mark, a miss for the
 * others). While a win is pending (CR25) a white "VICTORY" banner takes the place of the MPR on line 1: the compact
 * zone has no route area, and the banner replaces the X01 CHECKOUT banner. Its height is given by the layout policy.
 */
@Composable
fun CricketPlayerZone(content: CricketZoneContent, modifier: Modifier = Modifier) {
    val colors = KosmoTheme.colors
    val animate = LocalAnimationSettings.current.areAnimationsEnabled()
    val background by animateColorAsState(
        PlayerPalette.colorOf(content.slot, colors),
        zoneColorAnimationSpec(animate),
        label = "zone color",
    )
    val contentColor by animateColorAsState(
        PlayerPalette.onColorOf(content.slot, colors),
        zoneColorAnimationSpec(animate),
        label = "zone text color",
    )
    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .testTag(GameTags.CURRENT_PLAYER)
            .semantics { isTraversalGroup = true }
            .clip(KosmoShapes.card)
            .background(background)
            .padding(horizontal = ZONE_PADDING_HORIZONTAL, vertical = ZONE_PADDING_VERTICAL),
    ) {
        NameLine(content, contentColor)
        ScoreLine(content, contentColor)
    }
}

@Composable
private fun NameLine(content: CricketZoneContent, contentColor: Color) {
    val typography = KosmoTheme.typography
    val toThrow = stringResource(R.string.game_to_throw, content.name)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(INNER_GAP)) {
        Text(
            text = displayName(content.name),
            style = typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .testTag(GameTags.playerName(content.index))
                .semantics {
                    heading()
                    contentDescription = toThrow
                },
        )
        if (content.isVictory) {
            WinBanner()
        } else {
            Text(
                text = content.stat.text,
                style = typography.label,
                color = contentColor,
                maxLines = 1,
                modifier = Modifier.testTag(CricketTags.ZONE_STAT),
            )
        }
        content.indicator?.let { Chips(it, content.index) }
    }
}

/** CR25: the white banner (text `background`), as the filled visit box. */
@Composable
private fun WinBanner() {
    val colors = KosmoTheme.colors
    Box(
        modifier = Modifier
            .clip(KosmoShapes.control)
            .background(colors.onBackground)
            .padding(horizontal = BANNER_PADDING, vertical = 2.dp)
            .testTag(CricketTags.WIN_BANNER),
    ) {
        Text(
            text = stringResource(R.string.cricket_win_banner),
            style = KosmoTheme.typography.label,
            color = colors.background,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ScoreLine(content: CricketZoneContent, contentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(INNER_GAP)) {
        Text(
            text = content.score,
            style = KosmoTheme.typography.tileScore,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(CricketTags.ZONE_SCORE),
        )
        DartSlots(
            darts = content.turnDarts,
            nextDartIndex = content.nextDartIndex,
            isFinalDartHighlighted = false,
            modifier = Modifier.weight(1f),
            slotHeight = COMPACT_SLOT_HEIGHT,
        )
    }
}
