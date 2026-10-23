package io.github.darkcosmos.kosmodarts.ui.between

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.match.MatchScore
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.ui.app.GameMode
import io.github.darkcosmos.kosmodarts.ui.game.CheckoutSummary
import io.github.darkcosmos.kosmodarts.ui.game.MatchPosition

/**
 * Content of the between-legs screen (docs/rules/match.md, M16, M19), built from the match in pure code. No layout
 * decision here.
 *
 * @property winnerIndex the player who won the leg or the set that just ended.
 * @property winnerName his name.
 * @property winnerSlot his color slot: the zone takes his color (PC9).
 * @property isSetWon whether that leg also won a set: "SET 2 GAGNÉ" rather than "SET 2 · MANCHE 3 GAGNÉE".
 * @property wonPosition set and leg number of the leg just won, from the engine (M19).
 * @property checkout the winning visit ("CHECKOUT 141 · T20 T19 D12"), or `null` when it is not known (after a resume
 *   on this screen: the save keeps leg summaries only, ADR 0005).
 * @property hasSets whether the match has more than one set: only then the "Sets" column is shown (M14).
 * @property rows one row per player, in configuration order.
 * @property nextStarterName the player who starts the next leg (M6).
 * @property nextPosition set and leg number of the next leg, from the engine (M19).
 * @property nextStarterSlot the color slot of the next starter.
 * @property mode the game mode: it decides the statistic column (MATCH AVG, or MPR MATCH for Cricket, CR26).
 * @property winnerPoints Cricket only: the points of the leg winner ("245 PTS"), or `null` in a solo game (CR26).
 */
data class BetweenLegsState(
    val winnerIndex: Int,
    val winnerName: String,
    val winnerSlot: ColorSlot,
    val isSetWon: Boolean,
    val wonPosition: MatchPosition,
    val checkout: CheckoutSummary?,
    val hasSets: Boolean,
    val rows: List<Row>,
    val nextStarterName: String,
    val nextPosition: MatchPosition,
    val nextStarterSlot: ColorSlot,
    val mode: GameMode = GameMode.X01,
    val winnerPoints: Int? = null,
) {
    /**
     * @property setsWon sets won, or `null` when the match has a single set.
     * @property legsWon legs won in the current set.
     * @property average three-dart average over the match (M9), marks per round for Cricket (CR13), or `null` before
     *   the first dart.
     * @property isLegWinner whether this player won the leg that just ended: his figures are in `accent`.
     */
    data class Row(
        val name: String,
        val slot: ColorSlot,
        val setsWon: Int?,
        val legsWon: Int,
        val average: Double?,
        val isLegWinner: Boolean,
    )

    companion object {
        /**
         * The screen for [match], whose last leg was just confirmed with the winning visit [checkout], if known.
         *
         * @throws IllegalArgumentException if [match] has no confirmed leg.
         */
        fun of(match: X01Match, checkout: CheckoutSummary? = null): BetweenLegsState {
            val score = match.score
            val lastWinner = requireNotNull(score.legWinners.lastOrNull()) { "No confirmed leg" }
            val before = MatchScore(score.format, score.playerCount, score.legWinners.dropLast(1))
            val names = match.config.players
            return BetweenLegsState(
                winnerIndex = lastWinner,
                winnerName = names[lastWinner],
                winnerSlot = match.colorSlots[lastWinner],
                isSetWon = before.outcomeIfWonBy(lastWinner) == LegOutcome.SET,
                wonPosition = checkNotNull(MatchPosition.lastLeg(match)),
                checkout = checkout,
                hasSets = match.format.hasSets,
                rows = names.mapIndexed { player, name ->
                    Row(
                        name = name,
                        slot = match.colorSlots[player],
                        setsWon = score.setsWon[player].takeIf { match.format.hasSets },
                        legsWon = score.legsWonInSet[player],
                        average = match.playerStats[player].threeDartAverage,
                        isLegWinner = player == lastWinner,
                    )
                },
                nextStarterName = names[match.currentLeg.startingPlayerIndex],
                nextPosition = MatchPosition.current(match),
                nextStarterSlot = match.colorSlots[match.currentLeg.startingPlayerIndex],
            )
        }

        /**
         * The screen for the Cricket [match], whose last leg was just confirmed (CR26).
         *
         * @throws IllegalArgumentException if [match] has no confirmed leg.
         */
        fun of(match: CricketMatch): BetweenLegsState {
            val score = match.score
            val lastWinner = requireNotNull(score.legWinners.lastOrNull()) { "No confirmed leg" }
            val before = MatchScore(score.format, score.playerCount, score.legWinners.dropLast(1))
            val names = match.config.players
            val starter = match.currentLeg.startingPlayerIndex
            return BetweenLegsState(
                winnerIndex = lastWinner,
                winnerName = names[lastWinner],
                winnerSlot = match.colorSlots[lastWinner],
                isSetWon = before.outcomeIfWonBy(lastWinner) == LegOutcome.SET,
                wonPosition = checkNotNull(MatchPosition.lastLeg(score)),
                checkout = null,
                hasSets = match.format.hasSets,
                rows = names.mapIndexed { player, name ->
                    Row(
                        name = name,
                        slot = match.colorSlots[player],
                        setsWon = score.setsWon[player].takeIf { match.format.hasSets },
                        legsWon = score.legsWonInSet[player],
                        average = match.playerStats[player].marksPerRound,
                        isLegWinner = player == lastWinner,
                    )
                },
                nextStarterName = names[starter],
                nextPosition = MatchPosition.current(score),
                nextStarterSlot = match.colorSlots[starter],
                mode = GameMode.CRICKET,
                winnerPoints = match.completedLegs.last().points[lastWinner].takeIf { names.size > 1 },
            )
        }
    }
}
