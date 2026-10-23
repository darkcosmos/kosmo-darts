package io.github.darkcosmos.kosmodarts.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.app.GameMode

/**
 * The match statistic of the between-legs and end screens: the three-dart average for X01 (M9), the marks per round
 * for Cricket (CR13, CR26, CR27), or a dash before the first dart.
 */
@Composable
fun statisticText(mode: GameMode, value: Double?): String = when (mode) {
    GameMode.X01 -> averageText(value)
    GameMode.CRICKET ->
        formatMpr(value, LocalConfiguration.current.locales[0], stringResource(R.string.game_average_none))
}

/** The column header of the match statistic: "MATCH AVG", or "MPR MATCH" for Cricket. */
@Composable
fun statisticHeader(mode: GameMode): String = stringResource(
    when (mode) {
        GameMode.X01 -> R.string.match_average_header
        GameMode.CRICKET -> R.string.match_mpr_header
    },
)

/** What TalkBack reads for the match statistic [text] of a row on the between-legs screen. */
@Composable
fun statisticBetweenDescription(mode: GameMode, text: String): String = stringResource(
    when (mode) {
        GameMode.X01 -> R.string.game_average
        GameMode.CRICKET -> R.string.cricket_mpr_description
    },
    text,
)

/** What TalkBack reads for the match statistic [text] of a row on the end screen. */
@Composable
fun statisticEndDescription(mode: GameMode, text: String): String = stringResource(
    when (mode) {
        GameMode.X01 -> R.string.end_average
        GameMode.CRICKET -> R.string.cricket_mpr_description
    },
    text,
)
