package io.github.darkcosmos.kosmodarts.ui.between

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.game.AbandonDialog
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme

private val CONTENT_MAX_WIDTH = 560.dp
private val MIN_TOUCH_TARGET = 48.dp
private val NUMBER_COLUMN_WIDTH = 72.dp
private const val NAME_MAX_LINES = 2

/**
 * Between two legs of a match (docs/rules/match.md, M16): who won the leg or the set, the match score, who starts the
 * next leg, and "Next leg". A scrolling column inside the system bars, in portrait and landscape; the button stays at
 * the end of the content (U7). System back opens "Abandon the game?" ([isAbandonDialogShown]).
 */
@Composable
fun BetweenLegsScreen(
    match: X01Match,
    onEvent: (AppEvent) -> Unit,
    modifier: Modifier = Modifier,
    isAbandonDialogShown: Boolean = false,
) {
    val state = BetweenLegsState.of(match)
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = if (state.isSetWon) {
                        stringResource(R.string.between_set_won_by, state.winnerName)
                    } else {
                        stringResource(R.string.between_leg_won_by, state.winnerName)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .testTag(BetweenLegsTags.WINNER)
                        .semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.between_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                ScoreTable(state)
                Text(
                    text = stringResource(R.string.between_next_starter, state.nextStarterName),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag(BetweenLegsTags.NEXT_STARTER),
                )
                Button(
                    onClick = { onEvent(AppEvent.NextLegTapped) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = MIN_TOUCH_TARGET)
                        .testTag(BetweenLegsTags.NEXT_LEG),
                ) {
                    Text(stringResource(R.string.between_next_leg))
                }
            }
        }
        if (isAbandonDialogShown) AbandonDialog(onEvent)
    }
}

/** Column headers "Sets" (only with more than one set) and "Legs", then one row per player (U2). */
@Composable
private fun ScoreTable(state: BetweenLegsState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (state.hasSets) {
                HeaderCell(stringResource(R.string.match_sets_label), BetweenLegsTags.SETS_HEADER)
            }
            HeaderCell(stringResource(R.string.match_legs_label), BetweenLegsTags.LEGS_HEADER)
        }
        state.rows.forEachIndexed { index, row -> ScoreRow(row, index) }
    }
}

@Composable
private fun HeaderCell(text: String, tag: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .widthIn(min = NUMBER_COLUMN_WIDTH)
            .testTag(tag)
            .semantics { hideFromAccessibility() },
    )
}

/** A player's name (two lines at most, then an ellipsis) and numbers, read as "Ann, Sets 1, Legs 2". */
@Composable
private fun ScoreRow(row: BetweenLegsState.Row, index: Int) {
    val legs = stringResource(R.string.match_score_legs, row.legsWon)
    val score = row.setsWon?.let {
        stringResource(R.string.match_indicators_description, stringResource(R.string.match_score_sets, it), legs)
    } ?: legs
    val description = stringResource(R.string.match_indicators_description, row.name, score)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = NAME_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        row.setsWon?.let { NumberCell(it, BetweenLegsTags.sets(index)) }
        NumberCell(row.legsWon, BetweenLegsTags.legs(index))
    }
}

@Composable
private fun NumberCell(value: Int, tag: String) {
    Text(
        text = value.toString(),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .widthIn(min = NUMBER_COLUMN_WIDTH)
            .testTag(tag),
    )
}

@Preview
@Composable
private fun BetweenLegsScreenPreview() {
    val config = X01Config(listOf("Ann", "Bob"), startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
    // Ann 121, Bob misses, then Ann checks out with T20 T20 1.
    val firstLeg = List(3) { Dart.triple(20) } + List(3) { Dart.MISS } +
        listOf(Dart.triple(20), Dart.triple(20), Dart.single(1))
    val match = X01Match.start(X01MatchConfig(config, MatchFormat(legsPerSet = 3, sets = 2)))
    val next = firstLeg.fold(match) { current, dart -> current.throwDart(dart) }.confirmLeg()
    KosmoDartsTheme {
        BetweenLegsScreen(match = next, onEvent = {})
    }
}
