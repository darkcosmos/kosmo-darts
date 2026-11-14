package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketGame
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.journal.JournalReplayException
import io.github.darkcosmos.kosmodarts.engine.journal.LegReplay
import io.github.darkcosmos.kosmodarts.engine.journal.LegSetup
import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.engine.match.MatchScore
import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/**
 * The marks per round (MPR) shown during a Cricket match, between legs and at the end (docs/rules/cricket.md, CR13):
 * the useful marks of the darts of every leg (docs/rules/statistics.md, STC1) x 3 / the darts thrown. The darts of the
 * confirmed legs come from the match journal (the summaries do not keep them), the darts of the current leg from the
 * match itself, so an undo never leaves a voided dart behind (DJ2) and a saved game reloaded gives the same figures.
 */
object CricketMatchMpr {
    private const val DARTS_PER_ROUND = 3

    /**
     * The MPR of each player of [match], in configuration order, `null` for a player without a dart. If [journal] does
     * not describe the confirmed legs of [match] (a journal out of step, DJ4), the figures fall back to the marks of
     * the leg summaries, so that a screen never fails on them.
     */
    fun of(match: CricketMatch, journal: MatchJournal): List<Double?> {
        val darts = match.playerStats.map { it.dartsThrown }
        val useful = usefulMarksOf(match, journal) ?: allMarksOf(match)
        return useful.mapIndexed { player, marks ->
            if (darts[player] == 0) null else marks * DARTS_PER_ROUND.toDouble() / darts[player]
        }
    }

    private fun allMarksOf(match: CricketMatch): List<Int> = match.currentLeg.state.players.mapIndexed { player, now ->
        match.completedLegs.sumOf { it.marksHit[player] } + now.marksHit
    }

    private fun usefulMarksOf(match: CricketMatch, journal: MatchJournal): List<Int>? {
        val legs = playedLegs(match, journal) ?: return null
        val perLeg = legs.map { usefulPerPlayer(match.config, it) }.filterNotNull()
        return perLeg.takeIf { it.size == legs.size }?.let { all ->
            match.config.players.indices.map { player -> all.sumOf { it[player] } }
        }
    }

    /** A leg to replay: its darts in play, its starter and, if confirmed, the darts each player threw. */
    private class PlayedLeg(val darts: List<Dart>, val starter: Int, val thrown: List<Int>?)

    /** The confirmed legs replayed from [journal] (voided darts excluded), then the current leg, or `null`. */
    private fun playedLegs(match: CricketMatch, journal: MatchJournal): List<PlayedLeg>? {
        val completed = match.completedLegs.size
        if (journal.legs.size < completed) return null
        val config = match.config
        val winners = match.completedLegs.map { it.winnerIndex }
        return try {
            (0 until completed).map { leg ->
                val starter = MatchScore(match.format, config.players.size, winners.take(leg)).starterOfNextLeg
                val replayed = LegReplay.replay(journal.legs[leg], LegSetup.Cricket(config, starter))
                PlayedLeg(replayed.dartsInPlay, starter, match.completedLegs[leg].dartsThrown)
            } + PlayedLeg(match.currentLeg.darts, match.currentLeg.startingPlayerIndex, null)
        } catch (ignored: JournalReplayException) {
            null
        }
    }

    /** Useful marks of each player in a leg, or `null` if the replay does not match the summary's dart counts. */
    private fun usefulPerPlayer(config: CricketConfig, leg: PlayedLeg): List<Int>? {
        val yields = usefulMarks(config, leg.darts, leg.starter)
        if (!yields.isValid) return null
        val throwers = throwersOf(config, leg)
        val totals = MutableList(config.players.size) { 0 }
        throwers.forEachIndexed { i, thrower -> totals[thrower] += yields.yields[i].useful }
        val counts = config.players.indices.map { player -> throwers.count { it == player } }
        return totals.takeIf { leg.thrown == null || counts == leg.thrown }
    }

    /** The player who threw each dart of a leg that [usefulMarks] accepted (CR7, CR9). */
    private fun throwersOf(config: CricketConfig, leg: PlayedLeg): List<Int> {
        var game = CricketGame(config, emptyList(), leg.starter)
        return leg.darts.map { dart ->
            val thrower = checkNotNull(game.state.currentPlayerIndex) { "A dart follows the win" }
            game = game.throwDart(dart)
            thrower
        }
    }
}
