package io.github.darkcosmos.kosmodarts.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.darkcosmos.kosmodarts.R

// Parts of the game scoreboard: the score line of the highlighted card, the visit total box (docs/rules/visit-total.md)
// and the one-or-two-lines layout. Layout only: no game logic, nothing stored.

private val REMAINING_SIZE = 88.sp
private val LINE_GAP = 2.dp
private val BOX_PADDING = 8.dp

// Absorbs the rounding of the measured text width to whole pixels, so the box text never needs a second line.
private val BOX_SLACK = 1.dp

// VT11: a box fits BUST and this number, the highest visit total.
private const val BOX_NUMBER_REFERENCE = "180"

// VT11: a player name always gets at least the width of these six characters in its style.
internal const val NAME_MIN_REFERENCE: String = "Mmmmmm"

// VT11: the remaining score is laid out for the widest three-digit score, so the layout never switches as it drops.
private const val REMAINING_REFERENCE = "888"

// TalkBack order inside the highlighted card (VT8), after its name row (0) and before its dart slots (4).
private const val CARD_ORDER_REMAINING = 1f
private const val CARD_ORDER_BUST = 2f
private const val CARD_ORDER_BOX = 3f

/**
 * Remaining score of the highlighted card, then the BUST badge (while a bust waits) and the visit total box (VT9).
 * The remaining keeps the whole first line; the badge and the box go under it when the line is too narrow (VT11).
 */
@Composable
internal fun CardScoreLine(remaining: Int, isBust: Boolean, visitBox: VisitBoxText, index: Int, spacing: Dp) {
    val remainingStyle = LocalTextStyle.current.merge(
        TextStyle(fontSize = REMAINING_SIZE, lineHeight = REMAINING_SIZE, fontWeight = FontWeight.Bold),
    )
    OneOrTwoLines(
        minFirstWidth = measuredWidth(REMAINING_REFERENCE, remainingStyle),
        secondLineAlignment = Alignment.End,
        spacing = spacing,
        first = {
            Text(
                text = remaining.toString(),
                style = remainingStyle,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .testTag(GameTags.CURRENT_REMAINING)
                    .semantics { traversalIndex = CARD_ORDER_REMAINING },
            )
        },
        trailing = {
            if (isBust) {
                BustBadge(Modifier.semantics { traversalIndex = CARD_ORDER_BUST })
            }
            VisitTotalBox(
                text = visitBox,
                index = index,
                style = MaterialTheme.typography.titleLarge,
                borderColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.semantics { traversalIndex = CARD_ORDER_BOX },
            )
        },
    )
}

/** Shown in the card of the player whose bust turn waits for "Next player". */
@Composable
private fun BustBadge(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .testTag(GameTags.CARD_BUST)
            .semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Text(
            text = stringResource(R.string.game_bust),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

/**
 * Framed visit total of a player (VT8 to VT11). Its width fits the widest of BUST and [BOX_NUMBER_REFERENCE] in
 * [style], so it never changes with the value shown.
 */
@Composable
internal fun VisitTotalBox(
    text: VisitBoxText,
    index: Int,
    style: TextStyle,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    val args = text.formatArgs.toTypedArray()
    val value = stringResource(text.valueRes, *args)
    val description = stringResource(text.descriptionRes, *args)
    val bust = stringResource(R.string.game_bust)
    val textWidth = maxOf(measuredWidth(bust, style), measuredWidth(BOX_NUMBER_REFERENCE, style))
    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        border = BorderStroke(1.dp, borderColor),
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .width(textWidth + BOX_PADDING * 2 + BOX_SLACK)
            .testTag(GameTags.visitTotal(index))
            // One TalkBack node: the description is read instead of the bare number, which tests can still read.
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Text(
            text = value,
            style = style,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = BOX_PADDING, vertical = BOX_PADDING / 2),
        )
    }
}

/** Width of [text] laid out on one line in [style], at the current density and font scale. Render-only. */
@Composable
internal fun measuredWidth(text: String, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, style, density, measurer) {
        with(density) { measurer.measure(text, style, softWrap = false, maxLines = 1).size.width.toDp() }
    }
}

/**
 * Places [trailing] on the same line as [first] when [first] still gets at least [minFirstWidth]; otherwise [first]
 * takes the whole line and [trailing] goes on a second line, aligned with [secondLineAlignment] (VT11). Layout only:
 * no game logic, nothing stored.
 */
@Composable
internal fun OneOrTwoLines(
    minFirstWidth: Dp,
    secondLineAlignment: Alignment.Horizontal,
    spacing: Dp,
    modifier: Modifier = Modifier,
    first: @Composable () -> Unit,
    trailing: @Composable RowScope.() -> Unit,
) {
    Layout(
        contents = listOf(
            { Box { first() } },
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    content = trailing,
                )
            },
        ),
        modifier = modifier,
    ) { (firstMeasurables, trailingMeasurables), constraints ->
        val maxWidth = constraints.maxWidth
        val spacingPx = spacing.roundToPx()
        val trailingPlaceable = trailingMeasurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val firstWidthOnOneLine = maxWidth - spacingPx - trailingPlaceable.width
        val isOneLine = firstWidthOnOneLine >= minFirstWidth.roundToPx()
        val firstWidth = if (isOneLine) firstWidthOnOneLine else maxWidth
        val firstPlaceable = firstMeasurables.single().measure(
            Constraints(minWidth = firstWidth, maxWidth = firstWidth, maxHeight = constraints.maxHeight),
        )
        if (isOneLine) {
            val height = maxOf(firstPlaceable.height, trailingPlaceable.height)
            layout(maxWidth, height) {
                firstPlaceable.placeRelative(0, (height - firstPlaceable.height) / 2)
                trailingPlaceable.placeRelative(firstWidth + spacingPx, (height - trailingPlaceable.height) / 2)
            }
        } else {
            val secondLineTop = firstPlaceable.height + LINE_GAP.roundToPx()
            layout(maxWidth, secondLineTop + trailingPlaceable.height) {
                firstPlaceable.placeRelative(0, 0)
                val x = secondLineAlignment.align(trailingPlaceable.width, maxWidth, layoutDirection)
                trailingPlaceable.place(x, secondLineTop)
            }
        }
    }
}
