package io.github.darkcosmos.kosmodarts.engine.x01

import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome

/**
 * What to announce or play after a game action, without any text or sound (docs/rules/announcements.md). The app
 * turns cues into localized speech and sound effects.
 */
sealed interface X01Cue {
    /** AN1, AN25: [dart] scores at least one point. */
    data class DartHit(val dart: Dart) : X01Cue

    /** AN1: a dart that scores 0 (outside the scoring area). */
    data object DartMiss : X01Cue

    /** AN6: a dart was undone. */
    data object DartUndone : X01Cue

    /** AN2: a completed turn that scored [points], from 1 to 177. */
    data class TurnScore(val points: Int) : X01Cue

    /** AN2: a completed turn that scored 180. */
    data object OneEighty : X01Cue

    /** AN2: a completed turn that scored 0. */
    data object NoScore : X01Cue

    /** AN2: a turn that ended on a bust. */
    data object Bust : X01Cue

    /**
     * AN4: [name] throws next. [checkoutRemaining] is their remaining score when a checkout is possible for it (AN3),
     * else `null`. [isGameStart]: this is the first turn of the game (E1).
     */
    data class TurnStart(
        val playerIndex: Int,
        val name: String,
        val checkoutRemaining: Int?,
        val isGameStart: Boolean,
    ) : X01Cue

    /**
     * AN5, AN27: a winning dart. [outcome] tells what confirming the leg decides (docs/rules/match.md, M7); a single
     * game always decides the match.
     */
    sealed interface Win : X01Cue {
        val playerIndex: Int
        val name: String
        val outcome: LegOutcome
    }

    /** AN5: a win, from any remaining score but [X01Cues.BIG_FISH_REMAINING]. */
    data class GameShot(
        override val playerIndex: Int,
        override val name: String,
        override val outcome: LegOutcome = LegOutcome.MATCH,
    ) : Win

    /** AN5: a win whose turn started at [X01Cues.BIG_FISH_REMAINING]. */
    data class BigFish(
        override val playerIndex: Int,
        override val name: String,
        override val outcome: LegOutcome = LegOutcome.MATCH,
    ) : Win
}

/** Decides which cues a game action produces (docs/rules/announcements.md, AN1 to AN6, AN25). Pure. */
object X01Cues {

    /** Remaining score of the "big fish" checkout: T20, T20, bull (AN5). */
    const val BIG_FISH_REMAINING: Int = 170

    /** Highest score of a turn: three triple 20 (AN2). */
    const val MAX_TURN_SCORE: Int = 180

    /** AN6. */
    val dartUndone: X01Cue = X01Cue.DartUndone

    /**
     * AN1, AN2, AN5: cues of the dart that changed [before] into [after]: its dart cue, then the end-of-turn cue
     * when it completed a turn, which is the win cue for a checkout (E3).
     *
     * @throws IllegalArgumentException if [after] is not [before] plus exactly one dart.
     */
    fun afterDart(before: X01Game, after: X01Game): List<X01Cue> {
        require(after.config == before.config && after.darts.size == before.darts.size + 1 &&
            after.darts.subList(0, before.darts.size) == before.darts) {
            "afterDart expects exactly one more dart"
        }
        val dart = after.darts.last()
        val dartCue = if (dart.value == 0) X01Cue.DartMiss else X01Cue.DartHit(dart)
        val completedTurn = after.state.completedTurns.drop(before.state.completedTurns.size).singleOrNull()
        return listOfNotNull(dartCue, completedTurn?.let { endOfTurnCue(after, it) })
    }

    /**
     * AN1, AN2, AN5, AN27: cues of the dart that changed the current leg of [before] into that of [after]. The win
     * cue carries what confirming the leg decides (docs/rules/match.md, M7).
     *
     * @throws IllegalArgumentException if [after] is not [before] plus exactly one dart in the same leg.
     */
    fun afterDart(before: X01Match, after: X01Match): List<X01Cue> {
        require(after.matchConfig == before.matchConfig && after.completedLegs == before.completedLegs) {
            "afterDart expects the same leg"
        }
        val outcome = after.pendingOutcome
        return afterDart(before.currentLeg, after.currentLeg).map { cue ->
            when {
                outcome == null -> cue
                cue is X01Cue.GameShot -> cue.copy(outcome = outcome)
                cue is X01Cue.BigFish -> cue.copy(outcome = outcome)
                else -> cue
            }
        }
    }

    /** AN4: the player who throws next in [game], or `null` once the leg is finished. */
    fun turnStart(game: X01Game): X01Cue.TurnStart? {
        val index = game.state.currentPlayerIndex ?: return null
        val player = game.state.players[index]
        val checkoutRemaining = player.remaining.takeIf { Checkouts.isPossible(it, game.config.checkoutRule) }
        return X01Cue.TurnStart(index, player.name, checkoutRemaining, isGameStart = game.darts.isEmpty())
    }

    /** AN5: big fish when the winning [turn] started at [BIG_FISH_REMAINING], else game shot. */
    private fun winCue(game: X01Game, turn: X01Turn): X01Cue {
        val name = game.state.players[turn.playerIndex].name
        return if (turn.remainingBefore == BIG_FISH_REMAINING) {
            X01Cue.BigFish(turn.playerIndex, name)
        } else {
            X01Cue.GameShot(turn.playerIndex, name)
        }
    }

    private fun endOfTurnCue(game: X01Game, turn: X01Turn): X01Cue {
        val points = turn.darts.sumOf(Dart::value)
        return when {
            turn.outcome == TurnOutcome.CHECKOUT -> winCue(game, turn)
            turn.outcome == TurnOutcome.BUST -> X01Cue.Bust
            points == MAX_TURN_SCORE -> X01Cue.OneEighty
            points == 0 -> X01Cue.NoScore
            else -> X01Cue.TurnScore(points)
        }
    }
}
