package io.github.darkcosmos.kosmodarts.ui.between

import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.match.MatchScore
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match

/**
 * Content of the between-legs screen (docs/rules/match.md, M16), built from the match in pure code. No layout decision
 * here.
 *
 * @property winnerName the player who won the leg or the set that just ended.
 * @property isSetWon whether that leg also won a set: "Set won by …" rather than "Leg won by …" (U7).
 * @property hasSets whether the match has more than one set: only then the "Sets" column is shown (M14).
 * @property rows one row per player, in configuration order.
 * @property nextStarterName the player who starts the next leg (M6).
 */
data class BetweenLegsState(
    val winnerName: String,
    val isSetWon: Boolean,
    val hasSets: Boolean,
    val rows: List<Row>,
    val nextStarterName: String,
) {
    /**
     * @property setsWon sets won, or `null` when the match has a single set.
     * @property legsWon legs won in the current set.
     */
    data class Row(val name: String, val setsWon: Int?, val legsWon: Int)

    companion object {
        /**
         * The screen for [match], whose last leg was just confirmed.
         *
         * @throws IllegalArgumentException if [match] has no confirmed leg.
         */
        fun of(match: X01Match): BetweenLegsState {
            val score = match.score
            val lastWinner = requireNotNull(score.legWinners.lastOrNull()) { "No confirmed leg" }
            val before = MatchScore(score.format, score.playerCount, score.legWinners.dropLast(1))
            val names = match.config.players
            return BetweenLegsState(
                winnerName = names[lastWinner],
                isSetWon = before.outcomeIfWonBy(lastWinner) == LegOutcome.SET,
                hasSets = match.format.hasSets,
                rows = names.mapIndexed { player, name ->
                    Row(
                        name = name,
                        setsWon = score.setsWon[player].takeIf { match.format.hasSets },
                        legsWon = score.legsWonInSet[player],
                    )
                },
                nextStarterName = names[match.currentLeg.startingPlayerIndex],
            )
        }
    }
}
