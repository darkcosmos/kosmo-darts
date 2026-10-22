package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.ui.format.averageText
import io.github.darkcosmos.kosmodarts.ui.format.checkoutAnnouncement
import io.github.darkcosmos.kosmodarts.ui.format.checkoutDartsText
import io.github.darkcosmos.kosmodarts.ui.format.chipTexts
import io.github.darkcosmos.kosmodarts.ui.format.displayName
import io.github.darkcosmos.kosmodarts.ui.format.indicatorDescription
import io.github.darkcosmos.kosmodarts.ui.game.CheckoutSummary
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.game.MatchIndicator
import io.github.darkcosmos.kosmodarts.ui.game.VisitBoxText
import io.github.darkcosmos.kosmodarts.ui.game.ZoneMode
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.LocalAnimationSettings
import io.github.darkcosmos.kosmodarts.ui.theme.PlayerPalette
import io.github.darkcosmos.kosmodarts.ui.theme.fixedSp
import io.github.darkcosmos.kosmodarts.ui.theme.zoneColorAnimationSpec

private val ZONE_PADDING = 12.dp
private val INNER_GAP = 8.dp
private val BUST_SQUARE = 22.dp
private val CHIP_PADDING = 6.dp

/** Everything the current player's zone shows, mapped by the game screen from its state. */
class ZoneContent(
    val index: Int,
    val name: String,
    val slot: ColorSlot,
    val average: Double?,
    val indicator: MatchIndicator?,
    val mode: ZoneMode,
    val remaining: Int,
    val route: List<Dart>?,
    val turnDarts: List<Dart>,
    val nextDartIndex: Int?,
    val visitBox: VisitBoxText,
    val checkout: CheckoutSummary?,
)

/**
 * The current player's zone (design system, `CurrentPlayerZone`, 6.1 to 6.3): fixed in place, in the player's color
 * (`danger` during a BUST), its color fading in 200 ms on a player change (section 7). Top: name, average, chips;
 * middle: the score (or the CHECKOUT banner); bottom: the route area, then the dart slots and the filled visit box.
 * In landscape ([isLandscape]) the route stands right of the score.
 *
 * @param scoreSize the score size from the layout policy, in dp; the score shrinks further only if its area (the free
 *   height of the zone) is smaller than the policy estimated, so it is never clipped.
 */
@Composable
fun CurrentPlayerZone(
    content: ZoneContent,
    scoreSize: Float,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = KosmoTheme.colors
    val isBust = content.mode == ZoneMode.BUST
    val target = if (isBust) colors.danger else PlayerPalette.colorOf(content.slot, colors)
    val onTarget = if (isBust) colors.onDanger else PlayerPalette.onColorOf(content.slot, colors)
    val animate = LocalAnimationSettings.current.areAnimationsEnabled()
    val background by animateColorAsState(target, zoneColorAnimationSpec(animate), label = "zone color")
    val contentColor by animateColorAsState(onTarget, zoneColorAnimationSpec(animate), label = "zone text color")
    Column(
        verticalArrangement = Arrangement.spacedBy(INNER_GAP),
        modifier = modifier
            .testTag(GameTags.CURRENT_PLAYER)
            .semantics { isTraversalGroup = true }
            .clip(KosmoShapes.card)
            .background(background)
            .padding(ZONE_PADDING),
    ) {
        NameRow(content, contentColor)
        val area = Modifier.weight(1f)
        if (isLandscape) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(INNER_GAP),
                modifier = area.fillMaxWidth(),
            ) {
                ScoreArea(content, scoreSize, contentColor, Modifier.weight(1f))
                RouteArea(content.mode, content.route, isStacked = true, modifier = Modifier.weight(1f))
            }
        } else {
            ScoreArea(content, scoreSize, contentColor, area.fillMaxWidth())
            RouteArea(content.mode, content.route, isStacked = false)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(INNER_GAP),
        ) {
            DartSlots(
                darts = content.turnDarts,
                nextDartIndex = content.nextDartIndex,
                isFinalDartHighlighted = content.mode == ZoneMode.CHECKOUT,
                modifier = Modifier.weight(1f),
            )
            VisitBox(
                text = content.visitBox,
                index = content.index,
                isFilled = true,
                style = KosmoTheme.typography.visit,
                modifier = Modifier.height(DART_SLOT_HEIGHT),
            )
        }
    }
}

/** Name (ellipsized), average and the chips "S n" / "M n"; during a BUST, a square of the player's color first. */
@Composable
private fun NameRow(content: ZoneContent, contentColor: Color) {
    val colors = KosmoTheme.colors
    val typography = KosmoTheme.typography
    val toThrow = stringResource(R.string.game_to_throw, content.name)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(INNER_GAP)) {
        if (content.mode == ZoneMode.BUST) {
            Box(
                Modifier
                    .size(BUST_SQUARE)
                    .clip(KosmoShapes.control)
                    .background(PlayerPalette.colorOf(content.slot, colors))
                    .border(2.dp, colors.onBackground, KosmoShapes.control)
                    .clearAndSetSemantics {},
            )
        }
        Text(
            text = displayName(content.name),
            style = typography.playerNameZone,
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
        // As before: no average before the first dart of the match (M9).
        content.average?.let {
            Text(
                text = stringResource(R.string.game_average, averageText(it)),
                style = typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                modifier = Modifier.testTag(GameTags.average(content.index)),
            )
        }
        content.indicator?.let { Chips(it, content.index) }
    }
}

/** M14: "S1" "M1" on `background` with `onBackground` text, whatever the player's color. One TalkBack item. */
@Composable
private fun Chips(indicator: MatchIndicator, index: Int) {
    val colors = KosmoTheme.colors
    val description = indicatorDescription(indicator)
    Row(
        horizontalArrangement = Arrangement.spacedBy(CHIP_PADDING),
        modifier = Modifier
            .testTag(GameTags.matchIndicators(index))
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        chipTexts(indicator).forEach { chip ->
            Text(
                text = chip,
                style = KosmoTheme.typography.labelLarge,
                color = colors.onBackground,
                maxLines = 1,
                modifier = Modifier
                    .clip(KosmoShapes.control)
                    .background(colors.background)
                    .padding(horizontal = CHIP_PADDING, vertical = 2.dp),
            )
        }
    }
}

/** The score, or in CHECKOUT mode the `accent` banner "CHECKOUT" and "141 IN 3 DARTS" (design system 6.3). */
@Composable
private fun ScoreArea(
    content: ZoneContent,
    scoreSize: Float,
    contentColor: Color,
    modifier: Modifier,
) {
    BoxWithConstraints(contentAlignment = Alignment.Center, modifier = modifier) {
        val checkout = content.checkout
        if (content.mode == ZoneMode.CHECKOUT && checkout != null) {
            CheckoutBanner(checkout, contentColor)
        } else {
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val baseStyle = KosmoTheme.typography.scoreHuge
            // The size is chosen for the widest score, "888", so it never changes with the digits shown (no jump):
            // the policy size, or less if the area is smaller than the policy estimated (never clipped).
            val size = remember(constraints, baseStyle, scoreSize, density) {
                val reference = measurer.measure(
                    SCORE_REFERENCE,
                    baseStyle.copy(fontSize = density.fixedSp(scoreSize)),
                    softWrap = false,
                    maxLines = 1,
                ).size
                val fit = minOf(
                    1f,
                    constraints.maxWidth.toFloat() / reference.width.coerceAtLeast(1),
                    constraints.maxHeight.toFloat() / reference.height.coerceAtLeast(1),
                )
                scoreSize * fit
            }
            Text(
                text = content.remaining.toString(),
                style = baseStyle.copy(fontSize = density.fixedSp(size)),
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(GameTags.CURRENT_REMAINING),
            )
        }
    }
}

/** The widest remaining score in tabular figures. */
private const val SCORE_REFERENCE = "888"

/** U2, U5: the banner and its line are one polite live region, "Checkout, 141 in 3 darts". */
@Composable
private fun CheckoutBanner(checkout: CheckoutSummary, contentColor: Color) {
    val colors = KosmoTheme.colors
    val resources = LocalResources.current
    val dartCount = checkout.darts.size
    val announcement = checkoutAnnouncement(resources, checkout.points, dartCount)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(INNER_GAP),
        modifier = Modifier
            .testTag(GameTags.CHECKOUT_BANNER)
            .semantics(mergeDescendants = true) {
                contentDescription = announcement
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Text(
            text = stringResource(R.string.checkout_banner),
            style = KosmoTheme.typography.banner,
            color = colors.onAccent,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(maxFontSize = KosmoTheme.typography.banner.fontSize),
            modifier = Modifier
                .clip(KosmoShapes.control)
                .background(colors.accent)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Text(
            text = checkoutDartsText(resources, checkout.points, dartCount),
            style = KosmoTheme.typography.labelLarge,
            color = contentColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(GameTags.CHECKOUT_LINE),
        )
    }
}
