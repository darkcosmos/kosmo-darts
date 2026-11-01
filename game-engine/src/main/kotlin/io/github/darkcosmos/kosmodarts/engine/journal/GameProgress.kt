package io.github.darkcosmos.kosmodarts.engine.journal

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match

/**
 * The one place where the journal follows the match (DJ1, DJ2, DJ4; ADR 0011): whatever changes the match, the app
 * asks [follow] for the journal that goes with the new match, so that no caller can change one without the other.
 *
 * The match stays the source of truth for the rules; the journal only records what happened, with the time of the
 * caller's clock (the engine reads no clock).
 */
object GameProgress {

    /** The journal after the X01 match went from [before] to [after] at [at]; see [follow] for a cricket match. */
    fun follow(journal: MatchJournal, before: X01Match, after: X01Match, at: Long): MatchJournal = follow(
        journal,
        Step(before.completedLegs.size, before.currentLeg.darts, after.completedLegs.size, after.currentLeg.darts),
        at,
    )

    /** The journal after the Cricket match went from [before] to [after] at [at]. */
    fun follow(journal: MatchJournal, before: CricketMatch, after: CricketMatch, at: Long): MatchJournal = follow(
        journal,
        Step(before.completedLegs.size, before.currentLeg.darts, after.completedLegs.size, after.currentLeg.darts),
        at,
    )

    private class Step(val legsBefore: Int, val before: List<Dart>, val legsAfter: Int, val after: List<Dart>)

    /**
     * Unchanged when the darts in play did not change (an undo with nothing to undo is not journaled, DJ2); a dart
     * entered at [at]; an undo; or, when a leg was confirmed, the journal of the next leg.
     *
     * @throws IllegalStateException if the journal does not belong to the match, or the match changed in a way no
     *   dart, undo or leg confirmation explains.
     */
    private fun follow(journal: MatchJournal, step: Step, at: Long): MatchJournal {
        check(journal.legs.size == step.legsBefore + 1) { "The journal has a leg per leg of the match (DJ1)" }
        val before = step.before
        val after = step.after
        return when {
            step.legsAfter == step.legsBefore + 1 && after.isEmpty() -> journal.startNextLeg()
            step.legsAfter != step.legsBefore -> error("Only one leg can be confirmed at a time")
            after == before -> journal
            after.size == before.size + 1 && after.subList(0, before.size) == before -> journal.enter(after.last(), at)
            after.size == before.size - 1 && before.subList(0, after.size) == after -> journal.undo()
            else -> error("The darts in play changed by more than one dart or undo")
        }
    }
}
