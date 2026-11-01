package io.github.darkcosmos.kosmodarts.engine.archive

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.cricket.playLegWonBy
import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.engine.match.MatchRank
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.playLegWonBy
import io.github.darkcosmos.kosmodarts.engine.x01.throwing
import io.github.darkcosmos.kosmodarts.engine.cricket.throwing as cricketThrowing

internal const val MATCH_ID = "match-1"
internal const val STARTED_AT = 1_000L
internal const val ENDED_AT = 900_000L

/** An X01 match and its journal kept in step, the way the app plays them (entry times 2000, 2001, ...). */
internal class X01Session(var match: X01Match) {
    var journal = MatchJournal(MATCH_ID, STARTED_AT)
    private var clock = 2_000L

    fun throwing(vararg codes: String): X01Session = apply {
        sync { match.throwing(*codes) }
    }

    fun undo(): X01Session = apply {
        match = match.undo()
        journal = journal.undo()
    }

    fun playLegWonBy(winner: Int): X01Session = apply { sync { match.playLegWonBy(winner) } }

    fun confirmLeg(): X01Session = apply {
        match = match.confirmLeg()
        journal = journal.startNextLeg()
    }

    fun archive(): ArchivedMatch = MatchArchiver.archive(match, journal, ENDED_AT)

    private fun sync(play: () -> X01Match) {
        val before = match.currentLeg.darts.size
        match = play()
        match.currentLeg.darts.drop(before).forEach { journal = journal.enter(it, clock++) }
    }
}

/** A Cricket match and its journal kept in step. */
internal class CricketSession(var match: CricketMatch) {
    var journal = MatchJournal(MATCH_ID, STARTED_AT)
    private var clock = 2_000L

    fun throwing(vararg codes: String): CricketSession = apply { sync { match.cricketThrowing(*codes) } }

    fun playLegWonBy(winner: Int): CricketSession = apply { sync { match.playLegWonBy(winner) } }

    fun confirmLeg(): CricketSession = apply {
        match = match.confirmLeg()
        journal = journal.startNextLeg()
    }

    fun archive(): ArchivedMatch = MatchArchiver.archive(match, journal, ENDED_AT)

    private fun sync(play: () -> CricketMatch) {
        val before = match.currentLeg.darts.size
        match = play()
        match.currentLeg.darts.drop(before).forEach { journal = journal.enter(it, clock++) }
    }
}

internal fun List<ArchivedDart>.inLeg(ordinal: Int): List<ArchivedDart> = filter { it.legOrdinal == ordinal }

internal fun List<MatchRank>.ranksByPosition(): List<Int> = sortedBy { it.playerIndex }.map { it.rank }

internal fun ArchivedDart.dart(): Dart = Dart(segment, multiplier)
