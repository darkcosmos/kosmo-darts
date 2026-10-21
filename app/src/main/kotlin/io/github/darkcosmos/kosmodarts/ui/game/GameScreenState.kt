package io.github.darkcosmos.kosmodarts.ui.game

import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.engine.x01.TurnOutcome
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.engine.x01.X01Turn

/**
 * Content of the game screen: the match and its current leg, plus what the keypad and the screen show around it.
 * Immutable.
 *
 * When a dart completes a turn (third dart, bust or checkout), play does not move on for the players: the turn waits
 * for a human action, with the dart entry keys disabled: [nextPlayer] after a normal turn or a bust, "Finish …" or
 * [undo] in the checkout dialog. The engine has already passed the turn to the next player; the screen keeps showing
 * the player who threw until then.
 *
 * @property match the match; its current leg is [game] (docs/rules/match.md).
 * @property multiplier one-shot modifier for the next number: [Multiplier.SINGLE] when no modifier is active.
 * @property isAwaitingConfirmation whether the last dart completed a turn that is not confirmed yet.
 * @property isAbandonDialogShown whether the "Abandon the game?" confirmation is open.
 */
data class GameScreenState(
    val match: X01Match,
    val multiplier: Multiplier = Multiplier.SINGLE,
    val isAwaitingConfirmation: Boolean = false,
    val isAbandonDialogShown: Boolean = false,
) {
    /** A single game (docs/rules/match.md, M5): [game] is the only leg of a match in 1 leg and 1 set. */
    constructor(
        game: X01Game,
        multiplier: Multiplier = Multiplier.SINGLE,
        isAwaitingConfirmation: Boolean = false,
        isAbandonDialogShown: Boolean = false,
    ) : this(
        X01Match(X01MatchConfig(game.config), emptyList(), game),
        multiplier,
        isAwaitingConfirmation,
        isAbandonDialogShown,
    )

    /** The current leg: every per-leg figure (scores, turn, visit boxes, checkout suggestion) comes from it (M9). */
    val game: X01Game
        get() = match.currentLeg

    val canUndo: Boolean
        get() = game.canUndo

    /** M15: what "Finish …" decides while the checkout dialog is shown, else `null`. */
    val checkoutDialogOutcome: LegOutcome?
        get() = if (isCheckoutDialogShown) match.pendingOutcome else null

    /** M9: three-dart average of the player at [index] over the whole match, or `null` before their first dart. */
    fun averageOf(index: Int): Double? = match.playerStats[index].threeDartAverage

    /**
     * M14: sets and legs won by each player, in configuration order, or `null` in a single game. Only confirmed legs
     * count: a leg waiting in the checkout dialog is not won yet.
     */
    val indicators: List<MatchIndicator>?
        get() = if (match.format.isSingleGame) {
            null
        } else {
            val score = match.score
            score.legsWonInSet.indices.map { player ->
                MatchIndicator(
                    setsWon = score.setsWon[player].takeIf { match.format.hasSets },
                    legsWon = score.legsWonInSet[player],
                )
            }
        }

    /** The completed turn waiting for confirmation, or `null`. */
    val pendingTurn: X01Turn?
        get() = if (isAwaitingConfirmation) game.state.completedTurns.lastOrNull() else null

    /** "Next player" is enabled while a normal or bust turn waits; a checkout waits in its own dialog. */
    val canGoToNextPlayer: Boolean
        get() = isAwaitingConfirmation && pendingTurn?.outcome != TurnOutcome.CHECKOUT

    /** The winning dart waits in a modal dialog: "Finish game" or Undo. */
    val isCheckoutDialogShown: Boolean
        get() = pendingTurn?.outcome == TurnOutcome.CHECKOUT

    /** BUST is shown in the card of the player who threw while their bust turn waits. */
    val isBustShown: Boolean
        get() = pendingTurn?.outcome == TurnOutcome.BUST

    /** Numbers, Double, Triple, 25, Bull and Miss are disabled while a turn waits for confirmation. */
    val isKeypadEnabled: Boolean
        get() = !isAwaitingConfirmation && !game.state.isFinished

    /** Outer bull and bull take no multiplier (rule D2), so their keys are disabled while a modifier is active. */
    val areBullKeysEnabled: Boolean
        get() = isKeypadEnabled && multiplier == Multiplier.SINGLE

    /** Player shown as throwing: the one whose turn waits for confirmation, else the engine current player. */
    val highlightedPlayerIndex: Int?
        get() = pendingTurn?.playerIndex ?: game.state.currentPlayerIndex

    /**
     * Checkout route shown on the highlighted card (docs/rules/checkout-suggestions.md, CS5): `null` while a turn
     * waits (the engine has already moved to the next player), else the engine suggestion, which is then for the
     * highlighted player.
     */
    val checkoutSuggestion: List<Dart>?
        get() = if (isAwaitingConfirmation) null else game.checkoutSuggestion

    /** Darts of the highlighted player's turn: the pending turn, else the turn in progress. */
    val highlightedTurnDarts: List<Dart>
        get() = pendingTurn?.darts
            ?: game.state.currentPlayerIndex?.let { game.state.players[it].currentTurnDarts }.orEmpty()

    /** Activates [modifier], or cancels it if it is already active. */
    fun toggleMultiplier(modifier: Multiplier): GameScreenState = if (isKeypadEnabled) {
        copy(multiplier = if (multiplier == modifier) Multiplier.SINGLE else modifier)
    } else {
        this
    }

    fun throwNumber(number: Int): GameScreenState = record(Dart(number, multiplier))

    fun throwOuterBull(): GameScreenState = if (areBullKeysEnabled) record(Dart.OUTER_BULL) else this

    fun throwBull(): GameScreenState = if (areBullKeysEnabled) record(Dart.BULL) else this

    /** A miss is always a single miss: it also cancels an active modifier. */
    fun throwMiss(): GameScreenState = record(Dart.MISS)

    /** Moves play on after a completed turn. Does nothing if no turn is waiting, or on a checkout. */
    fun nextPlayer(): GameScreenState = if (canGoToNextPlayer) copy(isAwaitingConfirmation = false) else this

    /**
     * Removes the last dart, whoever threw it, and cancels an active modifier. On a pending turn, this reopens the
     * turn with its first darts.
     */
    fun undo(): GameScreenState =
        copy(match = match.undo(), multiplier = Multiplier.SINGLE, isAwaitingConfirmation = false)

    fun withAbandonDialog(shown: Boolean): GameScreenState = copy(isAbandonDialogShown = shown)

    private fun record(dart: Dart): GameScreenState {
        if (!isKeypadEnabled) return this
        val next = match.throwDart(dart)
        val turnCompleted = next.currentLeg.state.completedTurns.size > game.state.completedTurns.size
        return copy(match = next, multiplier = Multiplier.SINGLE, isAwaitingConfirmation = turnCompleted)
    }
}

/**
 * Sets and legs won by one player, shown on the game screen (docs/rules/match.md, M14). Built in pure code
 * ([GameScreenState.indicators]); the composable `MatchIndicators` only draws it.
 *
 * @property setsWon sets won, or `null` when the match has a single set (no set mention anywhere).
 * @property legsWon legs won in the current set.
 */
data class MatchIndicator(val setsWon: Int?, val legsWon: Int)
