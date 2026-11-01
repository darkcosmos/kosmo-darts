package io.github.darkcosmos.kosmodarts.engine.archive

import io.github.darkcosmos.kosmodarts.engine.board.nominalPoint
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.journal.JournalReplayException
import io.github.darkcosmos.kosmodarts.engine.journal.LegReplay
import io.github.darkcosmos.kosmodarts.engine.journal.LegSetup
import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.engine.match.MatchRank
import io.github.darkcosmos.kosmodarts.engine.match.MatchScore
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match

/** Builds the [ArchivedMatch] of a match whose win is pending confirmation (AR5, AR6). */
object MatchArchiver {
    /**
     * @throws IllegalArgumentException if the match is not won, or [journal] has not one leg per leg of the match, or
     *   its current leg does not hold the darts in play of the match's current leg (DJ4).
     * @throws JournalReplayException if a leg journal cannot be replayed.
     */
    fun archive(match: X01Match, journal: MatchJournal, endedAt: Long): ArchivedMatch {
        val config = match.config
        val input = Input(
            settings = ArchivedSettings.X01(config.startScore, config.checkoutRule, match.format),
            names = config.players,
            colorSlots = match.colorSlots,
            finalScore = requireNotNull(match.finalScore) { "The match is not won" },
            ranking = match.ranking,
            legSetups = List(match.completedLegs.size + 1) { LegSetup.X01(config, it % config.players.size) },
            currentLegDarts = match.currentLeg.darts,
        )
        return build(input, journal, endedAt)
    }

    /** Cricket overload of [archive]; same contract. */
    fun archive(match: CricketMatch, journal: MatchJournal, endedAt: Long): ArchivedMatch {
        val config = match.config
        val input = Input(
            settings = ArchivedSettings.Cricket(config.variant, config.isBullInPlay, match.format),
            names = config.players,
            colorSlots = match.colorSlots,
            finalScore = requireNotNull(match.finalScore) { "The match is not won" },
            ranking = match.ranking,
            legSetups = List(match.completedLegs.size + 1) { LegSetup.Cricket(config, it % config.players.size) },
            currentLegDarts = match.currentLeg.darts,
        )
        return build(input, journal, endedAt)
    }

    private class Input(
        val settings: ArchivedSettings,
        val names: List<String>,
        val colorSlots: List<ColorSlot>,
        val finalScore: MatchScore,
        val ranking: List<MatchRank>,
        val legSetups: List<LegSetup>,
        val currentLegDarts: List<Dart>,
    )

    private fun build(input: Input, journal: MatchJournal, endedAt: Long): ArchivedMatch {
        require(journal.legs.size == input.legSetups.size) { "The journal must have one entry per leg" }
        val replayed = journal.legs.zip(input.legSetups) { leg, setup -> LegReplay.replay(leg, setup) }
        require(replayed.all { it.isFinished }) { "Every journaled leg must be won" }
        require(replayed.last().dartsInPlay == input.currentLegDarts) {
            "The darts in play of the current leg journal differ from the match's current leg (DJ4)"
        }
        val score = input.finalScore
        val playerCount = score.playerCount
        val rankOf = input.ranking.associate { it.playerIndex to it.rank }
        val players = (0 until playerCount).map {
            ArchivedPlayer(
                position = it,
                name = input.names[it],
                colorSlot = input.colorSlots[it],
                rank = rankOf.getValue(it),
                legsWon = score.legsWonTotal[it],
                setsWon = score.setsWon[it],
            )
        }
        val legs = score.legWinners.mapIndexed { ordinal, winner ->
            val before = MatchScore(score.format, playerCount, score.legWinners.take(ordinal))
            ArchivedLeg(ordinal, before.currentSetNumber, before.currentLegNumber, ordinal % playerCount, winner)
        }
        val darts = replayed.flatMapIndexed { ordinal, leg ->
            leg.darts.map {
                ArchivedDart(
                    legOrdinal = ordinal,
                    playerPosition = it.playerIndex,
                    visit = it.visit,
                    rank = it.rank,
                    segment = it.dart.segment,
                    multiplier = it.dart.multiplier,
                    isBust = it.isBust,
                    isVoided = it.isVoided,
                    source = it.source,
                    enteredAt = it.enteredAt,
                    point = nominalPoint(it.dart),
                )
            }
        }
        return ArchivedMatch(
            matchId = journal.matchId,
            sessionType = SessionType.MATCH,
            startedAt = journal.startedAt,
            endedAt = endedAt,
            rulesVersion = ENGINE_RULES_VERSION,
            settings = input.settings,
            players = players,
            legs = legs,
            darts = darts,
        )
    }
}
