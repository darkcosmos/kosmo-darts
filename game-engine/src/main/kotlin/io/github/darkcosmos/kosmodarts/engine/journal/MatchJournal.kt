package io.github.darkcosmos.kosmodarts.engine.journal

import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/**
 * The journal of a match: one [LegJournal] per leg, the last one being the current leg (DJ1). Immutable.
 *
 * @property startedAt start of the match, milliseconds since the epoch.
 */
data class MatchJournal(
    val matchId: String,
    val startedAt: Long,
    val legs: List<LegJournal> = listOf(LegJournal()),
) {
    init {
        require(legs.isNotEmpty()) { "A match journal has at least one leg" }
    }

    /** The journal of the current leg. */
    val currentLeg: LegJournal
        get() = legs.last()

    /** Journals a dart entered at [at] in the current leg. */
    fun enter(dart: Dart, at: Long, source: DartSource = DartSource.MANUAL): MatchJournal =
        updateCurrent(currentLeg.with(JournalEvent.DartEntered(dart, at, source)))

    /**
     * Journals an undo in the current leg (DJ2). Call it only for an effective undo: an undo that changes nothing is
     * not journaled.
     */
    fun undo(): MatchJournal = updateCurrent(currentLeg.with(JournalEvent.Undone))

    /** Opens the journal of the next leg. */
    fun startNextLeg(): MatchJournal = copy(legs = legs + LegJournal())

    private fun updateCurrent(leg: LegJournal): MatchJournal = copy(legs = legs.dropLast(1) + leg)
}
