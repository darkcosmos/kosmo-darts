package io.github.darkcosmos.kosmodarts.ui.game

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.ui.format.checkoutRouteText
import io.github.darkcosmos.kosmodarts.ui.format.checkoutSpokenText
import io.github.darkcosmos.kosmodarts.ui.format.dartText

// Every dart a route can contain (CS1): the widest of their texts builds the reference line (CS8).
private val SCORING_DARTS: List<Dart> =
    Dart.NUMBERS.flatMap { number -> Multiplier.entries.map { Dart(number, it) } } + Dart.OUTER_BULL + Dart.BULL

private const val MAX_ROUTE_DARTS = 3

/**
 * Checkout suggestion line of the highlighted card (docs/rules/checkout-suggestions.md, CS7 to CS9). Always present:
 * its height is the height of [referenceText] at the current width and font scale, or more if [route] ever needs
 * more, so the card does not move when a suggestion appears or disappears and the text is never truncated. Render
 * only: the route comes from [GameScreenState.checkoutSuggestion].
 */
@Composable
internal fun CheckoutSuggestionLine(route: List<Dart>?, traversal: Float, modifier: Modifier = Modifier) {
    val style = LocalTextStyle.current.merge(MaterialTheme.typography.titleMedium)
    val text = route?.let { stringResource(R.string.game_checkout_suggestion, checkoutRouteText(it)) }.orEmpty()
    val description =
        route?.let { stringResource(R.string.game_checkout_suggestion_description, checkoutSpokenText(it)) }
    val measurer = rememberTextMeasurer()
    val reference = referenceText(style, measurer)
    val semantics = if (description == null) {
        // CS8: an empty line is silent for TalkBack.
        Modifier.clearAndSetSemantics { traversalIndex = traversal }
    } else {
        // One TalkBack node: the spoken description is read instead of the notation, which tests can still read.
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = description
            traversalIndex = traversal
        }
    }
    Layout(
        content = { Text(text = text, style = style, modifier = Modifier.testTag(GameTags.CHECKOUT_SUGGESTION)) },
        modifier = modifier
            .fillMaxWidth()
            .testTag(GameTags.CHECKOUT_SUGGESTION_LINE)
            .then(semantics),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val placeable = measurables.single().measure(Constraints(maxWidth = width))
        val reserved = measurer.measure(reference, style, constraints = Constraints(maxWidth = width)).size.height
        val height = maxOf(reserved, placeable.height)
        layout(width, height) { placeable.placeRelative(0, 0) }
    }
}

/**
 * CS8 reference line: the label, then three times the widest dart text in [style]. A route only wraps after a
 * separator, so each of its pieces (label and first dart, second dart, third dart) is never wider than the same
 * piece of this line: no route needs more lines than it does at any width.
 */
@Composable
private fun referenceText(style: TextStyle, measurer: TextMeasurer): String {
    val dartTexts = SCORING_DARTS.map { dartText(it) }
    val density = LocalDensity.current
    val widest = remember(dartTexts, style, density, measurer) {
        dartTexts.maxBy { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width }
    }
    val separator = stringResource(R.string.checkout_route_separator)
    return stringResource(R.string.game_checkout_suggestion, List(MAX_ROUTE_DARTS) { widest }.joinToString(separator))
}
