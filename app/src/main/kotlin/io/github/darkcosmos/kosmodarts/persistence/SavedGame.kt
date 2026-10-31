package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig

/**
 * The X01 game in progress as stored on the device (rules R4, R12, R14; [StoredGame]): the match, plus whether the
 * last completed turn of its current leg still waits for "Next player", and whether the between-legs screen is shown.
 * Everything else is derived by replay (docs/adr/0001-save-format.md, "Stored fields";
 * docs/adr/0005-save-format-v2-match.md). A finished leg always waits: it is shown with the checkout dialog.
 *
 * Depends on the engine only: the conversions to and from the screens live on the UI side
 * (`ui/game/SavedGameScreen.kt`; ADR 0002, consequences).
 */
data class SavedGame(
    val match: X01Match,
    override val isAwaitingNextPlayer: Boolean,
    override val isBetweenLegs: Boolean = false,
) : StoredGame {
    /** A single game (docs/rules/match.md, M5): [game] is the only leg of a match in 1 leg and 1 set. */
    constructor(game: X01Game, isAwaitingNextPlayer: Boolean) :
        this(X01Match(X01MatchConfig(game.config), emptyList(), game), isAwaitingNextPlayer)

    /** The current leg. */
    val game: X01Game
        get() = match.currentLeg

    /** Whether both stored flags agree with the replayed match (see [isConsistent] and ADR 0005). */
    override val isConsistent: Boolean
        get() = isConsistent(game, isAwaitingNextPlayer) && (!isBetweenLegs || isBetweenLegsPossible(match))

    companion object {
        /**
         * Whether a stored waiting flag is consistent with [game], read from the replayed engine state: a finished leg
         * must wait, and only a leg whose last dart completed a turn may wait.
         */
        fun isConsistent(game: X01Game, isAwaitingNextPlayer: Boolean): Boolean {
            val state = game.state
            val lastDartCompletedTurn = state.completedTurns.isNotEmpty() &&
                state.currentPlayerIndex?.let { state.players[it].currentTurnDarts.isEmpty() } ?: true
            return if (state.isFinished) isAwaitingNextPlayer else !isAwaitingNextPlayer || lastDartCompletedTurn
        }

        /**
         * The between-legs screen is only possible after a confirmed leg, before the first dart of the next one
         * (docs/rules/match.md, M16). The match is then never finished and nothing waits for "Next player".
         */
        private fun isBetweenLegsPossible(match: X01Match): Boolean =
            match.completedLegs.isNotEmpty() && match.currentLeg.darts.isEmpty()
    }
}
