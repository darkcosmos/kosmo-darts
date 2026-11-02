package io.github.darkcosmos.kosmodarts.engine.journal

import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/** Thrown when a journal cannot be replayed: an undo with nothing to undo, or a dart after the win (DJ2, DJ3). */
class JournalReplayException(message: String) : IllegalStateException(message)

/**
 * A replayed leg journal.
 *
 * @property darts every dart entered, voided or not, in entry order, with the values it had when entered (DJ3).
 * @property dartsInPlay the darts of the leg after the undos, in order (DJ4).
 * @property isFinished whether the leg is won after the last event.
 */
data class ReplayedLeg(
    val darts: List<JournalDart>,
    val dartsInPlay: List<Dart>,
    val isFinished: Boolean,
)

/** Replays a [LegJournal] into the engine leg, giving each dart its player, visit, rank, bust and voided flags. */
object LegReplay {

    /** @throws JournalReplayException if an undo has nothing to undo or a dart follows the win. */
    fun replay(journal: LegJournal, setup: LegSetup): ReplayedLeg {
        val end = journal.events.fold(Run(setup.newGame()), Run::apply)
        return ReplayedLeg(end.records, end.game.darts, end.game.isFinished)
    }

    private class Run(
        val game: LegGame,
        val records: List<JournalDart> = emptyList(),
        private val inPlay: List<Int> = emptyList(),
    ) {
        fun apply(event: JournalEvent): Run = when (event) {
            is JournalEvent.DartEntered -> enter(event)
            JournalEvent.Undone -> undo()
        }

        private fun enter(event: JournalEvent.DartEntered): Run {
            val player = game.currentPlayerIndex ?: throw JournalReplayException("Dart entered after the win")
            val record = JournalDart(
                dart = event.dart,
                enteredAt = event.enteredAt,
                source = event.source,
                playerIndex = player,
                visit = game.completedVisits + 1,
                rank = game.dartsInVisit + 1,
                isBust = false,
                isVoided = false,
            )
            val next = Run(game.throwDart(event.dart), records + record, inPlay + records.size)
            return next.refreshBust(record.visit)
        }

        private fun undo(): Run {
            val last = inPlay.lastOrNull() ?: throw JournalReplayException("Undo with nothing to undo")
            val voided = records[last]
            val updated = records.mapIndexed { i, r -> if (i == last) r.copy(isVoided = true) else r }
            return Run(game.undo(), updated, inPlay.dropLast(1)).refreshBust(voided.visit)
        }

        /** The darts in play of [visit] are bust when that visit now ends on a bust; voided darts keep their flag. */
        private fun refreshBust(visit: Int): Run {
            val bust = game.isBustVisit(visit)
            val updated = records.map { if (!it.isVoided && it.visit == visit) it.copy(isBust = bust) else it }
            return Run(game, updated, inPlay)
        }
    }
}
