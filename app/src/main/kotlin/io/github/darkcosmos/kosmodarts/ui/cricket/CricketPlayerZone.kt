package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.ui.components.Chips
import io.github.darkcosmos.kosmodarts.ui.components.DART_SLOT_HEIGHT
import io.github.darkcosmos.kosmodarts.ui.components.DartSlots
import io.github.darkcosmos.kosmodarts.ui.components.ZoneStat
import io.github.darkcosmos.kosmodarts.ui.format.displayName
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.game.MatchIndicator
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.LocalAnimationSettings
import io.github.darkcosmos.kosmodarts.ui.theme.PlayerPalette
import io.github.darkcosmos.kosmodarts.ui.theme.LocalKosmoTypography
import io.github.darkcosmos.kosmodarts.ui.theme.fixedSp
import io.github.darkcosmos.kosmodarts.ui.theme.zoneColorAnimationSpec

private val ZONE_PADDING_VERTICAL = 6.dp
private val ZONE_PADDING_HORIZONTAL = 10.dp
private val INNER_GAP = 8.dp
private val COMPACT_SLOT_HEIGHT = 36.dp
private val BANNER_PADDING = 8.dp
private val BANNER_PADDING_VERTICAL = 2.dp
private val SINGLE_LINE_PADDING_VERTICAL = 2.dp
private val SINGLE_LINE_SLOT_HEIGHT = 24.dp
private val SINGLE_LINE_SLOTS_WIDTH = 148.dp

/** Everything the compact zone of the Cricket game screen shows (CR22, CR25). */
class CricketZoneContent(
    val index: Int,
    val name: String,
    val slot: ColorSlot,
    val stat: ZoneStat,
    val indicator: MatchIndicator?,
    val isVictory: Boolean,
    val turnDarts: List<Dart>,
    val nextDartIndex: Int?,
    /** The player's points for the big total, `null` in a solo game (CR22, Q2: no total on the solo screen). */
    val total: Int? = null,
    /** The formatted MPR, shown large in a solo game (Q12 A). */
    val mprValue: String = "",
)

/**
 * The current player's compact zone (CR22): in the player's color. Line 1: name (one line, ellipsis), the [ZoneStat]
 * (MPR), the S/M chips; line 2: the dart slots (the darts that give a mark, a miss for the
 * others). While a win is pending (CR25) a white "VICTORY" banner takes the place of the MPR on line 1: the compact
 * zone has no route area, and the banner replaces the X01 CHECKOUT banner. Its height is given by the layout policy.
 *
 * With [isSingleLine] (landscape, CR22) everything stands on one line: name, MPR or banner, then the dart slots at
 * their minimum size; the S/M chips are then drawn by the screen, at the end of the title line.
 */
@Composable
fun CricketPlayerZone(
    content: CricketZoneContent,
    modifier: Modifier = Modifier,
    isSingleLine: Boolean = false,
    showsTotal: Boolean = false,
) {
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
        verticalArrangement = if (showsTotal && !isSingleLine) {
            Arrangement.spacedBy(INNER_GAP)
        } else {
            Arrangement.SpaceBetween
        },
        modifier = modifier
            .testTag(GameTags.CURRENT_PLAYER)
            .semantics { isTraversalGroup = true }
            .clip(KosmoShapes.card)
            .background(background)
            .padding(
                horizontal = ZONE_PADDING_HORIZONTAL,
                vertical = if (isSingleLine) SINGLE_LINE_PADDING_VERTICAL else ZONE_PADDING_VERTICAL,
            ),
    ) {
        if (isSingleLine) {
            SingleLine(content, contentColor)
        } else if (showsTotal) {
            // Solo: the MPR is the big number, so it is not repeated on the name line.
            NameLine(content, contentColor, hidesStat = content.total == null)
            BigNumber(content, contentColor, Modifier.weight(1f).fillMaxWidth())
            DartLine(content, DART_SLOT_HEIGHT)
        } else {
            NameLine(content, contentColor)
            DartLine(content, COMPACT_SLOT_HEIGHT)
        }
    }
}

/** The middle of the portrait zone: the total, or in a solo game a small "MPR" label over the big MPR. */
@Composable
private fun BigNumber(content: CricketZoneContent, contentColor: Color, modifier: Modifier) {
    val total = content.total
    if (total != null) {
        CricketZoneTotal(
            text = total.toString(),
            reference = ZONE_TOTAL_REFERENCE,
            description = pluralStringResource(R.plurals.cricket_header_points, total, total),
            tag = CricketTags.ZONE_TOTAL,
            contentColor = contentColor,
            modifier = modifier,
        )
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
            Text(
                text = stringResource(R.string.cricket_zone_mpr_label),
                style = KosmoTheme.typography.label,
                color = contentColor,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics { },
            )
            CricketZoneTotal(
                text = content.mprValue,
                reference = ZONE_MPR_REFERENCE,
                description = content.stat.text,
                tag = CricketTags.ZONE_MPR,
                contentColor = contentColor,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

/** Landscape: name and MPR (or the banner), then the three dart slots with a smaller, fixed dart text. */
@Composable
private fun SingleLine(content: CricketZoneContent, contentColor: Color) {
    val typography = KosmoTheme.typography
    val density = LocalDensity.current
    val small = typography.copy(
        dart = typography.dart.copy(fontSize = density.fixedSp(CricketBoardLayoutPolicy.LANDSCAPE_DART_SP)),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(INNER_GAP),
        modifier = Modifier.fillMaxHeight(),
    ) {
        NameLine(content, contentColor, Modifier.weight(1f), isSingleLine = true)
        CompositionLocalProvider(LocalKosmoTypography provides small) {
            DartSlots(
                darts = content.turnDarts,
                nextDartIndex = content.nextDartIndex,
                isFinalDartHighlighted = false,
                modifier = Modifier.width(SINGLE_LINE_SLOTS_WIDTH),
                slotHeight = SINGLE_LINE_SLOT_HEIGHT,
            )
        }
    }
}

@Composable
private fun NameLine(
    content: CricketZoneContent,
    contentColor: Color,
    modifier: Modifier = Modifier,
    isSingleLine: Boolean = false,
    hidesStat: Boolean = false,
) {
    val typography = KosmoTheme.typography
    val toThrow = stringResource(R.string.game_to_throw, content.name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(INNER_GAP),
        modifier = modifier,
    ) {
        Text(
            text = displayName(content.name),
            style = if (isSingleLine) typography.label else typography.labelLarge,
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
            WinBanner(isSingleLine)
        } else if (!hidesStat) {
            Text(
                text = content.stat.text,
                style = typography.label,
                color = contentColor,
                maxLines = 1,
                modifier = Modifier.testTag(CricketTags.ZONE_STAT),
            )
        }
        if (!isSingleLine) content.indicator?.let { Chips(it, content.index) }
    }
}

/** CR25: the white banner (text `background`), as the filled visit box. */
@Composable
private fun WinBanner(isSingleLine: Boolean) {
    val colors = KosmoTheme.colors
    Box(
        modifier = Modifier
            .clip(KosmoShapes.control)
            .background(colors.onBackground)
            .padding(horizontal = BANNER_PADDING, vertical = if (isSingleLine) 0.dp else BANNER_PADDING_VERTICAL)
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
private fun DartLine(content: CricketZoneContent, slotHeight: Dp) {
    DartSlots(
        darts = content.turnDarts,
        nextDartIndex = content.nextDartIndex,
        isFinalDartHighlighted = false,
        modifier = Modifier.fillMaxWidth(),
        slotHeight = slotHeight,
    )
}
