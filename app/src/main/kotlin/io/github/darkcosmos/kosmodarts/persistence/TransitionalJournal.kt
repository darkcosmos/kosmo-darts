package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.journal.JournalEvent
import io.github.darkcosmos.kosmodarts.engine.journal.LegJournal
import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match

/**
 * TRANSITIONAL, removed by task T6 of issue #50. Until the reducers keep the real journal next to the match, a saved
 * game that is given no journal gets one derived from its match, so that the game keeps being saved and resumed.
 *
 * The darts in play of the current leg are journaled as entered, one millisecond apart from 0; every confirmed leg gets
 * a made-up journal whose winner is the winner of its summary (its real darts are not stored), the other players
 * missing. The id is the nil UUID. Nothing here is ever archived: T6 replaces it with the real match id, start time and
 * journals, before any archive exists (T9).
 */
internal object TransitionalJournal {

    const val MATCH_ID: String = "00000000-0000-0000-0000-000000000000"

    private const val DARTS_PER_VISIT = 3
    private const val LEAVE_AT_LEAST = 2
    private val BY_VALUE_DESCENDING = Dart.SCORING.sortedByDescending { it.value }
    private val CRICKET_NUMBERS = listOf(20, 19, 18, 17, 16, 15).map(Dart::triple)

    fun of(match: X01Match): MatchJournal = build(
        winners = match.completedLegs.map { it.winnerIndex },
        playerCount = match.config.players.size,
        current = match.currentLeg.darts,
    ) { winner, starter -> x01WinningLeg(match.config, winner, starter) }

    fun of(match: CricketMatch): MatchJournal {
        val bulls = if (match.config.isBullInPlay) listOf(Dart.BULL, Dart.OUTER_BULL) else emptyList()
        val winnerDarts = CRICKET_NUMBERS + bulls
        return build(
            winners = match.completedLegs.map { it.winnerIndex },
            playerCount = match.config.players.size,
            current = match.currentLeg.darts,
        ) { winner, starter -> rotation(winner, starter, match.config.players.size, winnerDarts) }
    }

    private fun build(
        winners: List<Int>,
        playerCount: Int,
        current: List<Dart>,
        winningLeg: (winner: Int, starter: Int) -> List<Dart>,
    ): MatchJournal {
        var time = 0L
        fun leg(darts: List<Dart>) = LegJournal(darts.map { JournalEvent.DartEntered(it, time++) })
        val confirmed = winners.mapIndexed { index, winner -> leg(winningLeg(winner, index % playerCount)) }
        return MatchJournal(MATCH_ID, startedAt = 0, legs = confirmed + leg(current))
    }

    /** The winner scores down to a finish without ever busting; the others miss. */
    private fun x01WinningLeg(config: X01Config, winner: Int, starter: Int): List<Dart> {
        var remaining = config.startScore
        val singleOut = config.checkoutRule == CheckoutRule.SINGLE_OUT
        val finishing = BY_VALUE_DESCENDING.filter { singleOut || it.countsAsDouble }
        val winnerDarts = buildList {
            while (remaining > 0) {
                val dart = finishing.firstOrNull { it.value == remaining }
                    ?: BY_VALUE_DESCENDING.first { it.value <= remaining - LEAVE_AT_LEAST }
                add(dart)
                remaining -= dart.value
            }
        }
        return rotation(winner, starter, config.players.size, winnerDarts)
    }

    /** The darts of a leg in which [winner] throws [winnerDarts] in visits of three and the others miss. */
    private fun rotation(winner: Int, starter: Int, playerCount: Int, winnerDarts: List<Dart>): List<Dart> {
        val darts = mutableListOf<Dart>()
        val queue = winnerDarts.iterator()
        var player = starter
        while (queue.hasNext()) {
            repeat(DARTS_PER_VISIT) {
                if (player != winner) {
                    darts.add(Dart.MISS)
                } else if (queue.hasNext()) {
                    darts.add(queue.next())
                }
            }
            player = (player + 1) % playerCount
        }
        return darts
    }
}
