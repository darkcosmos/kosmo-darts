package io.github.darkcosmos.kosmodarts.ui.cricket

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketGame
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketTarget
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketTurn
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVisit
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVisits
import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.ui.game.MatchIndicator
import io.github.darkcosmos.kosmodarts.ui.game.MatchPosition

/**
 * Content of the Cricket game screen (docs/rules/cricket.md, CR22 to CR25): the match and its current leg, plus what
 * the keypad, the zone and the board show around it. Immutable; it mirrors the X01 `GameScreenState`.
 *
 * When a dart completes a turn (third dart or a win), play does not move on for the players: the turn waits for a human
 * action, with the dart entry keys disabled: [nextPlayer] after a normal turn, the confirm button (M15) or [undo] in
 * the VICTORY zone. The engine has already passed the turn on; the screen keeps showing the player who threw until
 * then, or the winner while a win is pending (CR25), even when the winning dart was thrown by another player (CR9).
 *
 * @property match the match; its current leg is [game] (docs/rules/match.md).
 * @property multiplier one-shot modifier for the next number: [Multiplier.SINGLE] when no modifier is active (CR24).
 * @property isAwaitingConfirmation whether the last dart completed a turn that is not confirmed yet.
 * @property isAbandonDialogShown whether the "Abandon the game?" confirmation is open.
 */
data class CricketGameScreenState(
    val match: CricketMatch,
    val multiplier: Multiplier = Multiplier.SINGLE,
    val isAwaitingConfirmation: Boolean = false,
    val isAbandonDialogShown: Boolean = false,
) {
    /** The current leg: every per-leg figure (marks, points, turn, visit boxes) comes from it (M9). */
    val game: CricketGame
        get() = match.currentLeg

    val canUndo: Boolean
        get() = game.canUndo

    /** The completed turn waiting for confirmation, or `null`; on a win, the turn of the winning dart's thrower. */
    val pendingTurn: CricketTurn?
        get() = if (isAwaitingConfirmation) game.state.completedTurns.lastOrNull() else null

    /** The winning dart waits in the VICTORY zone: the confirm button (M15) or Undo (CR25). */
    val isWinPending: Boolean
        get() = isAwaitingConfirmation && game.state.isFinished

    /** M15: what the confirm button of a pending win decides (its label), else `null`. */
    val winOutcome: LegOutcome?
        get() = if (isWinPending) match.pendingOutcome else null

    /** "Next player" is enabled while a normal turn waits; a win waits for its confirm button (M15, CR25). */
    val canGoToNextPlayer: Boolean
        get() = isAwaitingConfirmation && !isWinPending

    /** What the current player's zone shows (CR22, CR25). */
    val zoneMode: CricketZoneMode
        get() = if (isWinPending) CricketZoneMode.VICTORY else CricketZoneMode.NORMAL

    /**
     * Player shown in the zone and marked on the board: the winner while a win is pending (CR25), else the player
     * whose turn waits, else the engine current player.
     */
    val highlightedPlayerIndex: Int?
        get() = game.state.winnerIndex?.takeIf { isWinPending }
            ?: pendingTurn?.playerIndex
            ?: game.state.currentPlayerIndex

    /** Darts of the highlighted player's turn: his waiting turn, his turn in progress, or none (a non-thrower win). */
    val highlightedTurnDarts: List<Dart>
        get() {
            val highlighted = highlightedPlayerIndex ?: return emptyList()
            val pending = pendingTurn ?: return game.state.players[highlighted].currentTurnDarts
            return if (pending.playerIndex == highlighted) pending.darts else emptyList()
        }

    /** The dart slot of the next dart (underlined in `accent`), or `null` while a turn waits. */
    val nextDartIndex: Int?
        get() = if (isKeypadEnabled) highlightedTurnDarts.size else null

    /** Numbers, Double, Triple, 25, Bull and Miss are disabled while a turn waits for confirmation (CR24). */
    val isKeypadEnabled: Boolean
        get() = !isAwaitingConfirmation && !game.state.isFinished

    /** CR24: the 25 and Bull keys exist only with 25-Bull in play (CR1). */
    val areBullKeysShown: Boolean
        get() = game.config.isBullInPlay

    /** CR24, owner O2 A: 25 and Bull take no multiplier, so their keys are disabled while a modifier is active. */
    val areBullKeysEnabled: Boolean
        get() = areBullKeysShown && isKeypadEnabled && multiplier == Multiplier.SINGLE

    /** M14: sets and legs won by each player, in configuration order, or `null` in a single game. */
    val indicators: List<MatchIndicator>?
        get() = MatchIndicator.of(match.score)

    /** M14, M19: the position shown in the header, read from the engine (never counted here). */
    val headerPosition: MatchPosition
        get() = MatchPosition.current(match.score)

    /** The color slot of each player, in player order (docs/rules/players.md, PC6, PC9). */
    val colorSlots: List<ColorSlot>
        get() = match.colorSlots

    /** CR13: marks per round of the player at [index] over the whole match, or `null` before his first dart. */
    fun mprOf(index: Int): Double? = match.playerStats[index].marksPerRound

    /** CR22, owner D6: the points of the player at [index], or his closed targets in a solo game. */
    fun zoneScoreOf(index: Int): CricketZoneScore {
        val player = game.state.players[index]
        return if (game.config.players.size == 1) {
            CricketZoneScore.ClosedTargets(player.closedTargetCount, game.config.targets.size)
        } else {
            CricketZoneScore.Points(player.points)
        }
    }

    /**
     * CR14, CR23: one visit per player, in configuration order. While a turn waits, the engine current player has not
     * thrown yet, so he shows his last completed visit (VT6), like the X01 visit boxes.
     */
    val visits: List<CricketVisit>
        get() {
            val state = game.state
            val waitingNextPlayer = state.currentPlayerIndex.takeIf { isAwaitingConfirmation }
            return CricketVisits.of(state).mapIndexed { player, visit ->
                if (player == waitingNextPlayer) CricketVisits.lastCompleted(state, player) else visit
            }
        }

    /** CR23: one row per target in play, in board order (20 to 15, then Bull), with every player's marks. */
    val boardRows: List<CricketBoardRow>
        get() {
            val state = game.state
            return game.config.targets.map { target ->
                CricketBoardRow(
                    target = target,
                    marks = state.players.map { it.marksOn(target) },
                    isDead = target in state.deadTargets,
                )
            }
        }

    /** CR23: each player's points for the footer row, or `null` in a solo game (no points row). */
    val boardPoints: List<Int>?
        get() = game.state.players.map { it.points }.takeIf { it.size > 1 }

    /** Activates [modifier], or cancels it if it is already active. */
    fun toggleMultiplier(modifier: Multiplier): CricketGameScreenState = if (isKeypadEnabled) {
        copy(multiplier = if (multiplier == modifier) Multiplier.SINGLE else modifier)
    } else {
        this
    }

    fun throwNumber(number: Int): CricketGameScreenState = record(Dart(number, multiplier))

    fun throwOuterBull(): CricketGameScreenState = if (areBullKeysEnabled) record(Dart.OUTER_BULL) else this

    fun throwBull(): CricketGameScreenState = if (areBullKeysEnabled) record(Dart.BULL) else this

    /** A miss is always a single miss: it also cancels an active modifier. */
    fun throwMiss(): CricketGameScreenState = record(Dart.MISS)

    /** Moves play on after a completed turn. Does nothing if no turn is waiting, or on a pending win. */
    fun nextPlayer(): CricketGameScreenState = if (canGoToNextPlayer) copy(isAwaitingConfirmation = false) else this

    /**
     * Removes the last dart, whoever threw it, and cancels an active modifier (CR11). On a pending turn or win, this
     * reopens the turn with its first darts.
     */
    fun undo(): CricketGameScreenState =
        copy(match = match.undo(), multiplier = Multiplier.SINGLE, isAwaitingConfirmation = false)

    fun withAbandonDialog(shown: Boolean): CricketGameScreenState = copy(isAbandonDialogShown = shown)

    private fun record(dart: Dart): CricketGameScreenState {
        if (!isKeypadEnabled) return this
        val next = match.throwDart(dart)
        val turnCompleted = next.currentLeg.state.completedTurns.size > game.state.completedTurns.size
        return copy(match = next, multiplier = Multiplier.SINGLE, isAwaitingConfirmation = turnCompleted)
    }
}

/** What the current player's zone of the Cricket game screen shows (CR22, CR25). */
enum class CricketZoneMode {
    /** The player's name, MPR, points, darts and visit. */
    NORMAL,

    /** The winning dart waits for its confirm button (M15): the VICTORY banner, for the winner (CR25). */
    VICTORY,
}

/** The score of the zone (CR22): points, or the closed targets "4/7" in a solo game (owner Q8, D6). */
sealed interface CricketZoneScore {
    data class Points(val points: Int) : CricketZoneScore

    /** [closed] targets out of the [total] targets in play (6 or 7, CR1), read from the engine (review A2). */
    data class ClosedTargets(val closed: Int, val total: Int) : CricketZoneScore
}

/**
 * One row of the Cricket board (CR23).
 *
 * @property marks each player's marks on [target], in configuration order, from 0 to 3.
 * @property isDead whether every player has closed [target] (CR15).
 */
data class CricketBoardRow(val target: CricketTarget, val marks: List<Int>, val isDead: Boolean)
