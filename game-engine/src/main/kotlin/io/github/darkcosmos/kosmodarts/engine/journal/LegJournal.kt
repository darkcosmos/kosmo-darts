package io.github.darkcosmos.kosmodarts.engine.journal

/** The events of one leg, in entry order (DJ1). Immutable: every operation returns a new journal. */
data class LegJournal(val events: List<JournalEvent> = emptyList()) {

    /** Number of darts still in play: entered darts minus effective undos (DJ2). */
    val dartsInPlay: Int
        get() = events.count { it is JournalEvent.DartEntered } - events.count { it is JournalEvent.Undone }

    fun with(event: JournalEvent): LegJournal = LegJournal(events + event)
}
