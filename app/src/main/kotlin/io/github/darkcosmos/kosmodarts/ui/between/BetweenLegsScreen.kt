package io.github.darkcosmos.kosmodarts.ui.between

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
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
import io.github.darkcosmos.kosmodarts.ui.components.KosmoButton
import io.github.darkcosmos.kosmodarts.ui.components.ScoreCell
import io.github.darkcosmos.kosmodarts.ui.components.Scoreboard
import io.github.darkcosmos.kosmodarts.ui.components.ScoreboardRow
import io.github.darkcosmos.kosmodarts.ui.components.ScrollingScreen
import io.github.darkcosmos.kosmodarts.ui.components.TwoColumnScreen
import io.github.darkcosmos.kosmodarts.ui.components.WinnerZone
import io.github.darkcosmos.kosmodarts.ui.components.WinnerZoneTags
import io.github.darkcosmos.kosmodarts.ui.format.dartsText
import io.github.darkcosmos.kosmodarts.ui.format.displayName
import io.github.darkcosmos.kosmodarts.ui.format.formatText
import io.github.darkcosmos.kosmodarts.ui.format.positionText
import io.github.darkcosmos.kosmodarts.ui.format.statisticBetweenDescription
import io.github.darkcosmos.kosmodarts.ui.format.statisticHeader
import io.github.darkcosmos.kosmodarts.ui.format.statisticText
import io.github.darkcosmos.kosmodarts.ui.game.AbandonDialog
import io.github.darkcosmos.kosmodarts.ui.game.CheckoutSummary
import io.github.darkcosmos.kosmodarts.ui.layout.ScreenLayoutPolicy
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme
import io.github.darkcosmos.kosmodarts.ui.theme.PlayerPalette

private val AVERAGE_WIDTH = 84.dp
private val NEXT_MARK_WIDTH = 4.dp
private val NEXT_MARK_HEIGHT = 24.dp

/**
 * Between two legs of a match (mockup 08; docs/rules/match.md, M16, M19): the format line, the winner's zone with
 * "SET 2 · LEG 3 WON" (or "SET 2 WON") and the winning checkout, the scoreboard (sets when there are several, legs of
 * the set, match average; the leg winner's figures in `accent`), the info bar "SET 2 · LEG 4 ▌ CID STARTS", and
 * "NEXT LEG". System back opens "Abandon the game?" ([isAbandonDialogShown]).
 */
@Composable
fun BetweenLegsScreen(
    match: X01Match,
    onEvent: (AppEvent) -> Unit,
    modifier: Modifier = Modifier,
    isAbandonDialogShown: Boolean = false,
    checkout: CheckoutSummary? = null,
) {
    BetweenLegsContent(
        state = BetweenLegsState.of(match, checkout),
        formatLine = formatText(match.config.startScore, match.config.checkoutRule, match.format),
        onEvent = onEvent,
        modifier = modifier,
        isAbandonDialogShown = isAbandonDialogShown,
    )
}

/**
 * The between-legs layout of any mode, drawn from [state] (X01: [BetweenLegsScreen]; Cricket:
 * `CricketBetweenLegsScreen`). [formatLine] is the mode's format line.
 */
@Composable
fun BetweenLegsContent(
    state: BetweenLegsState,
    formatLine: String,
    onEvent: (AppEvent) -> Unit,
    modifier: Modifier = Modifier,
    isAbandonDialogShown: Boolean = false,
) {
    val format: @Composable ColumnScope.() -> Unit = {
        Text(
            text = formatLine,
            style = KosmoTheme.typography.label,
            color = KosmoTheme.colors.onBackgroundMuted,
        )
    }
    val winner: @Composable () -> Unit = {
        WinnerZone(
            chip = titleText(state),
            name = state.winnerName,
            slot = state.winnerSlot,
            tags = WinnerZoneTags(BetweenLegsTags.TITLE, BetweenLegsTags.WINNER, BetweenLegsTags.CHECKOUT),
            detail = state.checkout?.let {
                stringResource(R.string.between_checkout, it.points, dartsText(it.darts))
            } ?: state.winnerPoints?.let { stringResource(R.string.between_points, it) },
            modifier = Modifier.fillMaxWidth().testTag(BetweenLegsTags.WINNER_ZONE),
        )
    }
    val nextLeg: @Composable () -> Unit = {
        KosmoButton(
            text = stringResource(R.string.between_next_leg),
            onClick = { onEvent(AppEvent.NextLegTapped) },
            minHeight = 58.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(BetweenLegsTags.NEXT_LEG),
        )
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (ScreenLayoutPolicy.hasTwoColumns(maxWidth.value, maxHeight.value)) {
            // Owner review of PR #57: the scoreboard right of the winner, "Next leg" right under the scoreboard.
            TwoColumnScreen(
                top = format,
                start = {
                    winner()
                    NextStarterBar(state)
                },
                end = {
                    MatchScoreboard(state)
                    nextLeg()
                },
            )
        } else {
            ScrollingScreen(
                buttonCount = 1,
                content = {
                    format()
                    winner()
                    MatchScoreboard(state)
                    NextStarterBar(state)
                },
                buttons = { nextLeg() },
            )
        }
    }
    if (isAbandonDialogShown) AbandonDialog(onEvent)
}

/** M19: "SET 2 WON" on a set win, else "SET 2 · LEG 3 WON", or "LEG 3 WON" with one set. */
@Composable
private fun titleText(state: BetweenLegsState): String {
    val set = state.wonPosition.setNumber
    val leg = state.wonPosition.legNumber ?: 1
    return when {
        state.isSetWon && set != null -> stringResource(R.string.between_set_won, set)
        set != null -> stringResource(R.string.between_leg_won, set, leg)
        else -> stringResource(R.string.between_leg_won_no_set, leg)
    }
}

/** M16: SETS (more than one set), LEGS of the set, MATCH AVG; the leg winner's figures in `accent`. */
@Composable
private fun MatchScoreboard(state: BetweenLegsState) {
    val headers = listOfNotNull(
        ScoreCell(stringResource(R.string.match_sets_label), tag = BetweenLegsTags.SETS_HEADER)
            .takeIf { state.hasSets },
        ScoreCell(stringResource(R.string.match_legs_short), tag = BetweenLegsTags.LEGS_HEADER),
        ScoreCell(statisticHeader(state.mode), width = AVERAGE_WIDTH),
    )
    val separator = stringResource(R.string.checkout_spoken_separator)
    val rows = state.rows.mapIndexed { index, row ->
        val average = statisticText(state.mode, row.average)
        val description = listOfNotNull(
            displayName(row.name),
            row.setsWon?.let { stringResource(R.string.match_score_sets, it) },
            stringResource(R.string.match_score_legs, row.legsWon),
            statisticBetweenDescription(state.mode, average),
        ).joinToString(separator)
        ScoreboardRow(
            name = row.name,
            slot = row.slot,
            rank = null,
            cells = listOfNotNull(
                row.setsWon?.let { ScoreCell(it.toString(), row.isLegWinner, BetweenLegsTags.sets(index)) },
                ScoreCell(row.legsWon.toString(), row.isLegWinner, BetweenLegsTags.legs(index)),
                ScoreCell(average, tag = BetweenLegsTags.average(index), width = AVERAGE_WIDTH),
            ),
            description = description,
        )
    }
    Scoreboard(
        headers,
        stringResource(R.string.match_player_header),
        rows,
        Modifier.fillMaxWidth().testTag(BetweenLegsTags.SCOREBOARD),
    )
}

/** "SET 2 · LEG 4 ▌ CID STARTS": the next leg's position (M19) and its starter (M6), one TalkBack item. */
@Composable
private fun NextStarterBar(state: BetweenLegsState) {
    val colors = KosmoTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        modifier = Modifier
            .fillMaxWidth()
            .clip(KosmoShapes.card)
            .background(colors.surface)
            .padding(16.dp)
            .testTag(BetweenLegsTags.NEXT_STARTER)
            .semantics(mergeDescendants = true) {},
    ) {
        positionText(state.nextPosition)?.let {
            Text(text = it, style = KosmoTheme.typography.labelLarge, color = colors.onBackgroundMuted)
        }
        Box(
            Modifier
                .width(NEXT_MARK_WIDTH)
                .height(NEXT_MARK_HEIGHT)
                .background(PlayerPalette.colorOf(state.nextStarterSlot, colors)),
        )
        Text(
            text = stringResource(R.string.between_starts, displayName(state.nextStarterName)),
            style = KosmoTheme.typography.labelLarge,
            color = colors.onBackground,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Preview(widthDp = 412, heightDp = 915)
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
