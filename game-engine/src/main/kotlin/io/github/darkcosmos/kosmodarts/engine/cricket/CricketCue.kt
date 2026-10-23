package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/**
 * What to announce or play after a Cricket action, without any text or sound (docs/rules/announcements.md, AN29 to
 * AN32). A separate type from `X01Cue`: Cricket has no turn score, 180, bust or remaining score (AN32).
 */
sealed interface CricketCue {
    /** AN29, AN30: [dart] gives at least one mark on a target that was not dead before it. */
    data class DartHit(val dart: Dart) : CricketCue

    /** AN30, CR15: [dart] gives at least one mark on a target that was already dead before it. */
    data class ClosedSectorHit(val dart: Dart) : CricketCue

    /** AN29: a dart that gives no mark. */
    data object DartMiss : CricketCue

    /** AN6: a dart was undone. */
    data object DartUndone : CricketCue

    /** AN31: [name] throws next. [isGameStart]: this is the first turn of the leg's game (E1). */
    data class TurnStart(val playerIndex: Int, val name: String, val isGameStart: Boolean) : CricketCue

    /** AN27, AN31: a winning dart. [outcome] tells what confirming the leg decides (docs/rules/match.md, M7). */
    data class Win(val playerIndex: Int, val name: String, val outcome: LegOutcome = LegOutcome.MATCH) : CricketCue
}

/** Decides which cues a Cricket action produces (docs/rules/announcements.md, AN29 to AN32). Pure. */
object CricketCues {

    /** AN6. */
    val dartUndone: CricketCue = CricketCue.DartUndone

    /**
     * AN29 to AN31: cues of the dart that changed [before] into [after]: its dart cue, then the win cue when it ended
     * the leg. The winner may not be the player who threw (CR8).
     *
     * @throws IllegalArgumentException if [after] is not [before] plus exactly one dart.
     */
    fun afterDart(before: CricketGame, after: CricketGame): List<CricketCue> {
        require(after.config == before.config && after.darts.size == before.darts.size + 1 &&
            after.darts.subList(0, before.darts.size) == before.darts) {
            "afterDart expects exactly one more dart"
        }
        val dart = after.darts.last()
        val dartCue = when {
            CricketMarks.of(dart, after.config).count == 0 -> CricketCue.DartMiss
            before.isClosedSectorHit(dart) -> CricketCue.ClosedSectorHit(dart)
            else -> CricketCue.DartHit(dart)
        }
        val winner = after.state.winnerIndex
        val winCue = winner?.let { CricketCue.Win(it, after.state.players[it].name) }
        return listOfNotNull(dartCue, winCue)
    }

    /**
     * AN29 to AN31, AN27: like the game version, and the win cue carries what confirming the leg decides.
     *
     * @throws IllegalArgumentException if [after] is not [before] plus exactly one dart in the same leg.
     */
    fun afterDart(before: CricketMatch, after: CricketMatch): List<CricketCue> {
        require(after.matchConfig == before.matchConfig && after.completedLegs == before.completedLegs) {
            "afterDart expects the same leg"
        }
        val outcome = after.pendingOutcome
        return afterDart(before.currentLeg, after.currentLeg).map { cue ->
            if (cue is CricketCue.Win && outcome != null) cue.copy(outcome = outcome) else cue
        }
    }

    /** AN31: the player who throws next in [game], or `null` once the leg is finished. */
    fun turnStart(game: CricketGame): CricketCue.TurnStart? {
        val index = game.state.currentPlayerIndex ?: return null
        return CricketCue.TurnStart(index, game.state.players[index].name, isGameStart = game.darts.isEmpty())
    }
}
