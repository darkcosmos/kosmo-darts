package io.github.darkcosmos.kosmodarts.ui.end

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.GameMode
import io.github.darkcosmos.kosmodarts.ui.components.ButtonKind
import io.github.darkcosmos.kosmodarts.ui.components.KosmoButton
import io.github.darkcosmos.kosmodarts.ui.components.ScoreCell
import io.github.darkcosmos.kosmodarts.ui.components.Scoreboard
import io.github.darkcosmos.kosmodarts.ui.components.ScoreboardRow
import io.github.darkcosmos.kosmodarts.ui.components.ScrollingScreen
import io.github.darkcosmos.kosmodarts.ui.components.WinnerZone
import io.github.darkcosmos.kosmodarts.ui.components.WinnerZoneTags
import io.github.darkcosmos.kosmodarts.ui.format.displayName
import io.github.darkcosmos.kosmodarts.ui.format.formatText
import io.github.darkcosmos.kosmodarts.ui.format.statisticEndDescription
import io.github.darkcosmos.kosmodarts.ui.format.statisticHeader
import io.github.darkcosmos.kosmodarts.ui.format.statisticText
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme

private val AVERAGE_WIDTH = 84.dp
private val DARTS_WIDTH = 60.dp
private val POINTS_WIDTH = 60.dp
private val BUTTON_MIN_HEIGHT = 58.dp

/**
 * End of a match (mockup 09; M17), a single game included: the format line, the winner's zone with "MATCH WINNER"
 * (no "2 SETS TO 0" line, owner Q8), the ranking (ties share a rank) with the sets column, the legs column with one
 * set, no score column in a single game, then the match average and the darts thrown over the match (owner O2), and
 * "REMATCH" then "NEW GAME".
 */
@Composable
fun EndScreen(match: X01Match, onEvent: (AppEvent) -> Unit, modifier: Modifier = Modifier) {
    EndContent(
        rows = endRows(match),
        formatLine = formatText(match.config.startScore, match.config.checkoutRule, match.format),
        mode = GameMode.X01,
        onEvent = onEvent,
        modifier = modifier,
    )
}

/**
 * The end layout of any mode, drawn from [rows] (X01: [EndScreen]; Cricket: `CricketEndScreen`). [formatLine] is the
 * mode's format line; [mode] decides the statistic column and the "NEW GAME" mode.
 */
@Composable
fun EndContent(
    rows: List<EndRow>,
    formatLine: String,
    mode: GameMode,
    onEvent: (AppEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollingScreen(
        buttonCount = 2,
        modifier = modifier,
        content = {
            Text(
                text = formatLine,
                style = KosmoTheme.typography.label,
                color = KosmoTheme.colors.onBackgroundMuted,
            )
            rows.firstOrNull { it.isWinner }?.let { winner ->
                WinnerZone(
                    chip = stringResource(R.string.end_match_winner),
                    name = winner.name,
                    slot = winner.slot,
                    tags = WinnerZoneTags(EndTags.TITLE, EndTags.WINNER, EndTags.DETAIL),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Ranking(rows, mode)
        },
        buttons = {
            KosmoButton(
                text = stringResource(R.string.end_rematch),
                onClick = { onEvent(AppEvent.Rematch) },
                minHeight = BUTTON_MIN_HEIGHT,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EndTags.REMATCH),
            )
            KosmoButton(
                text = stringResource(R.string.home_new_game),
                onClick = { onEvent(AppEvent.NewGame(mode)) },
                kind = ButtonKind.SECONDARY,
                minHeight = BUTTON_MIN_HEIGHT,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EndTags.NEW_GAME),
            )
        },
    )
}

/** M17, owner Q8: SETS (more than one set), else LEGS (one set), else none; then MATCH AVG and DARTS (owner O2). */
@Composable
private fun Ranking(rows: List<EndRow>, mode: GameMode) {
    val showsSets = rows.any { it.setsWon != null }
    val showsLegs = !showsSets && rows.any { it.legsWon != null }
    val scoreHeader = when {
        showsSets -> stringResource(R.string.match_sets_label)
        showsLegs -> stringResource(R.string.match_legs_label)
        else -> null
    }
    val showsPoints = rows.any { it.points != null }
    val headers = listOfNotNull(
        scoreHeader?.let { ScoreCell(it, tag = EndTags.SCORE_HEADER) },
        ScoreCell(stringResource(R.string.match_points_header), tag = EndTags.POINTS_HEADER, width = POINTS_WIDTH)
            .takeIf { showsPoints },
        ScoreCell(statisticHeader(mode), width = AVERAGE_WIDTH),
        ScoreCell(stringResource(R.string.end_darts_header), tag = EndTags.DARTS_HEADER, width = DARTS_WIDTH),
    )
    val separator = stringResource(R.string.checkout_spoken_separator)
    val scoreboardRows = rows.mapIndexed { position, row ->
        val score = if (showsSets) row.setsWon else row.legsWon.takeIf { showsLegs }
        val average = statisticText(mode, row.threeDartAverage)
        val darts = pluralStringResource(R.plurals.end_darts_thrown, row.dartsThrown, row.dartsThrown)
        val description = listOfNotNull(
            row.rank.toString(),
            displayName(row.name),
            score?.let {
                stringResource(if (showsSets) R.string.match_score_sets else R.string.match_score_legs, it)
            },
            row.points?.let { stringResource(R.string.cricket_points_description, it) },
            statisticEndDescription(mode, average),
            darts,
        ).joinToString(separator)
        ScoreboardRow(
            name = row.name,
            slot = row.slot,
            rank = row.rank,
            cells = listOfNotNull(
                score?.let { ScoreCell(it.toString(), tag = EndTags.score(position)) },
                row.points?.let { ScoreCell(it.toString(), tag = EndTags.points(position), width = POINTS_WIDTH) },
                ScoreCell(average, tag = EndTags.average(position), width = AVERAGE_WIDTH),
                ScoreCell(row.dartsThrown.toString(), tag = EndTags.darts(position), width = DARTS_WIDTH),
            ),
            description = description,
            tag = EndTags.row(position),
        )
    }
    Scoreboard(headers, stringResource(R.string.match_player_header), scoreboardRows, Modifier.fillMaxWidth())
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun EndScreenPreview() {
    val config = X01Config(listOf("Ann", "Bob"), startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
    val darts = List(3) { Dart.triple(20) } +
        List(3) { Dart.single(20) } +
        listOf(Dart.triple(20), Dart.triple(20), Dart.single(1))
    KosmoDartsTheme {
        EndScreen(match = X01Match(X01MatchConfig(config), emptyList(), X01Game(config, darts)), onEvent = {})
    }
}
