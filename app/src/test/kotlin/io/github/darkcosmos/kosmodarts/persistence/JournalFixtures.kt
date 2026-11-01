package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.journal.JournalEvent
import io.github.darkcosmos.kosmodarts.engine.journal.LegJournal
import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/** Journals used by the persistence tests (ADR 0011). Times are made up. */
internal object JournalFixtures {

    const val MATCH_ID = "1b4e28ba-2d1c-41f1-9b7e-6a2f1c0d9e55"
    const val STARTED_AT = 1_793_520_000_000L

    /** Time of the first dart of a journal built by [leg]. */
    const val FIRST_DART_AT = 1_793_520_001_200L

    /** A dart event [at] the given time. */
    fun dart(dart: Dart, at: Long): JournalEvent = JournalEvent.DartEntered(dart, at)

    /** A leg journal of these darts, one second apart from [FIRST_DART_AT], none voided. */
    fun leg(darts: List<Dart>): LegJournal =
        LegJournal(darts.mapIndexed { index, dart -> dart(dart, FIRST_DART_AT + index * 1_000L) })

    /** [journal] with the id and start time of the fixtures instead of the transitional ones. */
    fun identified(journal: MatchJournal): MatchJournal = journal.copy(matchId = MATCH_ID, startedAt = STARTED_AT)

    /** [pairs] darts entered and undone at once, 20 bytes each in the file (times have 13 digits). */
    fun voided(pairs: Int): List<JournalEvent> =
        List(pairs) { listOf(dart(Dart.triple(20), FIRST_DART_AT), JournalEvent.Undone) }.flatten()

    /** [journal] with [pairs] voided darts at the start of each of its first [legs] legs. */
    fun withVoided(journal: MatchJournal, legs: Int, pairs: Int): MatchJournal = journal.copy(
        legs = journal.legs.mapIndexed { index, leg ->
            if (index < legs) LegJournal(voided(pairs) + leg.events) else leg
        },
    )

    /** [lines] of a saved game with this many journals: the last journal line, repeated, and the matching count. */
    fun withJournalCount(lines: List<String>, count: Int): List<String> {
        val journal = lines.last { it.startsWith("journal=") }
        return lines.filterNot { it.startsWith("journal=") }
            .map { if (it.startsWith("journals=")) "journals=$count" else it } + List(count) { journal }
    }
}
