package io.github.darkcosmos.kosmodarts.ui.game

import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.match.MatchScore
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
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
 * for a human action, with the dart entry keys disabled: [nextPlayer] after a normal turn or a bust, the confirm
 * button (M15) or [undo] in the CHECKOUT zone. The engine has already passed the turn to the next player; the screen
 * keeps showing the player who threw until then.
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

    /** M15: what the confirm button of a pending checkout decides (its label), else `null`. */
    val checkoutOutcome: LegOutcome?
        get() = if (isCheckoutPending) match.pendingOutcome else null

    /** M9: three-dart average of the player at [index] over the whole match, or `null` before their first dart. */
    fun averageOf(index: Int): Double? = match.playerStats[index].threeDartAverage

    /**
     * M14: sets and legs won by each player, in configuration order, or `null` in a single game. Only confirmed legs
     * count: a pending checkout is not won yet.
     */
    val indicators: List<MatchIndicator>?
        get() = MatchIndicator.of(match.score)

    /** The completed turn waiting for confirmation, or `null`. */
    val pendingTurn: X01Turn?
        get() = if (isAwaitingConfirmation) game.state.completedTurns.lastOrNull() else null

    /** "Next player" is enabled while a normal or bust turn waits; a checkout waits for its confirm button (M15). */
    val canGoToNextPlayer: Boolean
        get() = isAwaitingConfirmation && pendingTurn?.outcome != TurnOutcome.CHECKOUT

    /** The winning dart waits in the CHECKOUT zone: the confirm button (M15) or Undo. */
    val isCheckoutPending: Boolean
        get() = pendingTurn?.outcome == TurnOutcome.CHECKOUT

    /** The bust turn of the highlighted player waits for "Next player" (VT13). */
    val isBustPending: Boolean
        get() = pendingTurn?.outcome == TurnOutcome.BUST

    /** What the current player's zone shows (design system 6.1 to 6.3), derived from the pending turn. */
    val zoneMode: ZoneMode
        get() = when {
            isCheckoutPending -> ZoneMode.CHECKOUT
            isBustPending -> ZoneMode.BUST
            else -> ZoneMode.NORMAL
        }

    /** Color slot of the player at [index] (docs/rules/players.md, PC6, PC9). */
    fun colorSlotOf(index: Int): ColorSlot = match.colorSlots[index]

    /**
     * M6: the other players' tiles, in play order starting from the player after the highlighted one. Empty with one
     * player or once the leg has no highlighted player.
     */
    val tileOrder: List<Int>
        get() {
            val highlighted = highlightedPlayerIndex ?: return emptyList()
            val count = game.config.players.size
            return (1 until count).map { (highlighted + it) % count }
        }

    /** M14, M19: the position shown in the header, read from the engine (never counted here). */
    val headerPosition: MatchPosition
        get() = MatchPosition.current(match)

    /** The pending checkout (design system 6.3), or `null`: points scored by the turn and its darts. */
    val checkoutSummary: CheckoutSummary?
        get() = pendingTurn?.takeIf { isCheckoutPending }?.let { CheckoutSummary(it.remainingBefore, it.darts) }

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
     * Checkout route shown in the current player's zone (docs/rules/checkout-suggestions.md, CS5, CS8): `null` while
     * a turn waits (BUST and CHECKOUT modes included; the engine has already moved to the next player) or when no
     * route exists, else the engine suggestion, which is then for the highlighted player.
     */
    val checkoutSuggestion: List<Dart>?
        get() = if (isAwaitingConfirmation) null else game.checkoutSuggestion

    /** Darts of the highlighted player's turn: the pending turn, else the turn in progress. */
    val highlightedTurnDarts: List<Dart>
        get() = pendingTurn?.darts
            ?: game.state.currentPlayerIndex?.let { game.state.players[it].currentTurnDarts }.orEmpty()

    /** The dart slot of the next dart (underlined in `accent`), or `null` while a turn waits. */
    val nextDartIndex: Int?
        get() = if (isKeypadEnabled) highlightedTurnDarts.size else null

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
 * Sets and legs won by one player, shown as the chips "S n" and "M n" of the zone and the line "S n · M n" of the
 * tiles (docs/rules/match.md, M14). Built in pure code ([GameScreenState.indicators]); composables only draw it.
 *
 * @property setsWon sets won, or `null` when the match has a single set (no set mention anywhere).
 * @property legsWon legs won in the current set.
 */
data class MatchIndicator(val setsWon: Int?, val legsWon: Int) {
    companion object {
        /** M14: one indicator per player of [score], in configuration order, or `null` in a single game. */
        fun of(score: MatchScore): List<MatchIndicator>? = if (score.format.isSingleGame) {
            null
        } else {
            score.legsWonInSet.indices.map { player ->
                MatchIndicator(
                    setsWon = score.setsWon[player].takeIf { score.format.hasSets },
                    legsWon = score.legsWonInSet[player],
                )
            }
        }
    }
}

/** What the current player's zone shows (docs/design/design-system.md, 6.1 to 6.3). */
enum class ZoneMode {
    /** The remaining score, the checkout route and the turn in progress. */
    NORMAL,

    /** A bust waits for "Next player": the zone turns `danger` with the BUST banner (VT13). */
    BUST,

    /** The winning dart waits for its confirm button (M15): the CHECKOUT banner replaces the score. */
    CHECKOUT,
}

/**
 * A position in the match, as numbers read from the engine (M14, M19): [setNumber] only when the match has more than
 * one set, [legNumber] only outside a single game. Composables turn it into "SET 2 · MANCHE 3", "MANCHE 3" or nothing.
 */
data class MatchPosition(val setNumber: Int?, val legNumber: Int?) {
    companion object {
        /** The leg in progress of [match]. */
        fun current(match: X01Match): MatchPosition = current(match.score)

        /** The last confirmed leg of [match], or `null` before the first one (between legs, M16). */
        fun lastLeg(match: X01Match): MatchPosition? = lastLeg(match.score)

        /** The leg in progress of a match with [score], whatever its mode (X01 or Cricket). */
        fun current(score: MatchScore): MatchPosition =
            of(score.format, score.currentSetNumber, score.currentLegNumber)

        /** The last confirmed leg of a match with [score], or `null` before the first one (M16). */
        fun lastLeg(score: MatchScore): MatchPosition? {
            val set = score.lastLegSetNumber
            val leg = score.lastLegNumber
            return if (set == null || leg == null) null else of(score.format, set, leg)
        }

        private fun of(format: MatchFormat, set: Int, leg: Int): MatchPosition = when {
            format.isSingleGame -> MatchPosition(null, null)
            format.hasSets -> MatchPosition(set, leg)
            else -> MatchPosition(null, leg)
        }
    }
}

/**
 * A pending checkout (design system 6.3, U2): "141 EN 3 FLÉCHETTES".
 *
 * @property points the remaining score at the start of the winning turn, scored by it.
 * @property darts the darts of the winning turn; the last one is the final dart.
 */
data class CheckoutSummary(val points: Int, val darts: List<Dart>)
