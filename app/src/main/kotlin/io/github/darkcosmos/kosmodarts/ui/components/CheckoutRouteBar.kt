package io.github.darkcosmos.kosmodarts.ui.components

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.ui.format.checkoutRouteText
import io.github.darkcosmos.kosmodarts.ui.format.checkoutSpokenText
import io.github.darkcosmos.kosmodarts.ui.format.dartText
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.game.ZoneMode
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme

private const val MAX_ROUTE_DARTS = 3
private val BAR_PADDING = 8.dp
private val LABEL_PADDING = 12.dp

/**
 * The area of the zone under the score (design system 6.1 to 6.3): the checkout route in NORMAL mode
 * (docs/rules/checkout-suggestions.md, CS7 to CS9), the white BUST banner in BUST mode (VT13); the zone does not
 * show it in CHECKOUT mode (U10). Its height is always reserved for the widest possible route at the current width
 * and font scale (CS8), so the zone and the score never move when a route appears or disappears. [isSingleLine]: the
 * darts never wrap and shrink to fit the width instead, for the landscape zone where the route stands right of the
 * score (owner review of PR #57); in portrait they wrap between two darts.
 */
@Composable
fun RouteArea(mode: ZoneMode, route: List<Dart>?, isSingleLine: Boolean, modifier: Modifier = Modifier) {
    val referenceText = checkoutRouteText(referenceRoute())
    val label = stringResource(R.string.checkout_route_label)
    val labelStyle = KosmoTheme.typography.labelLarge
    val routeStyle = KosmoTheme.typography.route
    val measurer = rememberTextMeasurer()
    // CS9: the area is one TalkBack item while it shows a route, read "Checkout: Triple 20, Triple 20, Bull"; it is
    // silent when empty (CS8). The BUST banner is its own live region.
    val description = route?.takeIf { mode == ZoneMode.NORMAL }
        ?.let { stringResource(R.string.game_checkout_suggestion_description, checkoutSpokenText(it)) }
    Layout(
        content = {
            when {
                mode == ZoneMode.BUST -> BustBanner()
                mode == ZoneMode.NORMAL && route != null -> CheckoutRouteBar(route, isSingleLine)
                else -> Unit
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag(GameTags.CHECKOUT_SUGGESTION_LINE)
            .then(
                if (description == null) {
                    Modifier
                } else {
                    Modifier.semantics(mergeDescendants = true) { contentDescription = description }
                },
            ),
    ) { content, constraints ->
        val width = constraints.maxWidth
        val child = Constraints(minWidth = width, maxWidth = width)
        // The height of the bar showing the reference route, measured as CheckoutRouteBar lays it out.
        val horizontal = LABEL_PADDING.roundToPx()
        val vertical = BAR_PADDING.roundToPx()
        val labelSize = measurer.measure(label, labelStyle, softWrap = false, maxLines = 1).size
        val labelHeight = labelSize.height + 2 * vertical
        val dartsWidth = width - labelSize.width - 4 * horizontal
        // On one line the darts shrink to fit, so a line at the full size is the most they need.
        val dartsHeight = measurer.measure(
            referenceText,
            routeStyle,
            softWrap = !isSingleLine,
            maxLines = if (isSingleLine) 1 else Int.MAX_VALUE,
            constraints = Constraints(maxWidth = maxOf(1, dartsWidth)),
        ).size.height + vertical
        val reserved = maxOf(labelHeight, dartsHeight)
        val placeables = content.map { it.measure(child.copy(minHeight = reserved)) }
        val height = maxOf(reserved, placeables.maxOfOrNull { it.height } ?: 0)
        layout(width, height) { placeables.forEach { it.placeRelative(0, 0) } }
    }
}

/**
 * The route on one panel: the label box "CHECKOUT" / « SORTIE » on `accent`, as high as the panel and sharing its
 * rounded start corners, then the darts in `accent` on `background`, without separator dots (CS7). [isSingleLine]: the
 * darts shrink to stay on one line. [RouteArea] makes it one TalkBack item (CS9).
 */
@Composable
private fun CheckoutRouteBar(route: List<Dart>, isSingleLine: Boolean) {
    val colors = KosmoTheme.colors
    val typography = KosmoTheme.typography
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(KosmoShapes.control)
            .background(colors.background),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight()
                .background(colors.accent)
                .testTag(GameTags.CHECKOUT_ROUTE_LABEL),
        ) {
            Text(
                text = stringResource(R.string.checkout_route_label),
                style = typography.labelLarge,
                color = colors.onAccent,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = LABEL_PADDING, vertical = BAR_PADDING),
            )
        }
        Text(
            text = checkoutRouteText(route),
            style = typography.route,
            color = colors.accent,
            maxLines = if (isSingleLine) 1 else Int.MAX_VALUE,
            softWrap = !isSingleLine,
            autoSize = if (isSingleLine) TextAutoSize.StepBased(maxFontSize = typography.route.fontSize) else null,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = LABEL_PADDING, vertical = BAR_PADDING / 2)
                .testTag(GameTags.CHECKOUT_SUGGESTION),
        )
    }
}

/** VT13, U5: the white banner "BUST" in `danger`, announced by TalkBack when it appears (polite live region). */
@Composable
private fun BustBanner() {
    val colors = KosmoTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(KosmoShapes.control)
            .background(colors.onBackground)
            .testTag(GameTags.CARD_BUST)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = stringResource(R.string.game_bust),
            style = KosmoTheme.typography.route.copy(letterSpacing = KosmoTheme.typography.banner.letterSpacing),
            color = colors.danger,
            textAlign = TextAlign.Center,
            // The same height as the darts line of the route, so the banner fits the reserved height (CS8).
            modifier = Modifier.padding(vertical = BAR_PADDING / 2).testTag(GameTags.CARD_BUST_TEXT),
        )
    }
}

/**
 * CS8 reference route: three times the widest dart text in the route style. A route only wraps between two darts,
 * so no route needs more height than this one at any width.
 */
@Composable
private fun referenceRoute(): List<Dart> {
    val resources = LocalResources.current
    val measurer = rememberTextMeasurer()
    val style = KosmoTheme.typography.route
    return remember(resources, style, measurer) { checkoutReferenceRoute(resources, style, measurer) }
}

/** [referenceRoute] outside composition, so that tests measure exactly the same route. */
fun checkoutReferenceRoute(resources: Resources, style: TextStyle, measurer: TextMeasurer): List<Dart> {
    val widest = Dart.SCORING.maxBy { measurer.measure(dartText(resources, it), style, softWrap = false).size.width }
    return List(MAX_ROUTE_DARTS) { widest }
}
