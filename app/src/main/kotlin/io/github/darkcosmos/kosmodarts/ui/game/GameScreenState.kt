package io.github.darkcosmos.kosmodarts.ui.game

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.engine.x01.TurnOutcome
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01Turn

/**
 * Content of the game screen: the leg, plus what the keypad and the screen show around it. Immutable.
 *
 * @property multiplier one-shot modifier for the next number: [Multiplier.SINGLE] when no modifier is active.
 * @property isBustShown whether the last dart was a bust; shown until the next dart or undo.
 * @property isAbandonDialogShown whether the "Abandon the game?" confirmation is open.
 */
data class GameScreenState(
    val game: X01Game,
    val multiplier: Multiplier = Multiplier.SINGLE,
    val isBustShown: Boolean = false,
    val isAbandonDialogShown: Boolean = false,
) {
    val canUndo: Boolean
        get() = game.canUndo

    /** Outer bull and bull take no multiplier (rule D2), so their keys are disabled while a modifier is active. */
    val areBullKeysEnabled: Boolean
        get() = multiplier == Multiplier.SINGLE

    /** Last completed turn, whoever threw it, or `null` before the first one. */
    val lastTurn: X01Turn?
        get() = game.state.completedTurns.lastOrNull()

    /** Activates [modifier], or cancels it if it is already active. */
    fun toggleMultiplier(modifier: Multiplier): GameScreenState =
        copy(multiplier = if (multiplier == modifier) Multiplier.SINGLE else modifier)

    fun throwNumber(number: Int): GameScreenState = record(Dart(number, multiplier))

    fun throwOuterBull(): GameScreenState = if (areBullKeysEnabled) record(Dart.OUTER_BULL) else this

    fun throwBull(): GameScreenState = if (areBullKeysEnabled) record(Dart.BULL) else this

    /** A miss is always a single miss: it also cancels an active modifier. */
    fun throwMiss(): GameScreenState = record(Dart.MISS)

    /** Removes the last dart, whoever threw it, and cancels an active modifier. */
    fun undo(): GameScreenState = copy(game = game.undo(), multiplier = Multiplier.SINGLE, isBustShown = false)

    fun withAbandonDialog(shown: Boolean): GameScreenState = copy(isAbandonDialogShown = shown)

    private fun record(dart: Dart): GameScreenState {
        if (game.state.isFinished) return this
        val next = game.throwDart(dart)
        val turnEnded = next.state.completedTurns.size > game.state.completedTurns.size
        val isBust = turnEnded && next.state.completedTurns.last().outcome == TurnOutcome.BUST
        return copy(game = next, multiplier = Multiplier.SINGLE, isBustShown = isBust)
    }
}
