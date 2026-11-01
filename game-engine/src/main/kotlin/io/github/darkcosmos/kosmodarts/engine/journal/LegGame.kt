package io.github.darkcosmos.kosmodarts.engine.journal

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketGame
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.TurnOutcome
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game

/** The kind of leg a journal is replayed into, with its configuration and the player who starts it (DJ3). */
sealed interface LegSetup {
    val startingPlayerIndex: Int

    data class X01(val config: X01Config, override val startingPlayerIndex: Int = 0) : LegSetup

    data class Cricket(val config: CricketConfig, override val startingPlayerIndex: Int = 0) : LegSetup
}

/** What the replay needs from an engine leg, whatever the game. */
internal interface LegGame {
    val darts: List<Dart>
    val isFinished: Boolean

    /** Player who throws next, or `null` once the leg is won. */
    val currentPlayerIndex: Int?

    /** Darts already thrown in the visit in progress. */
    val dartsInVisit: Int

    /** Number of completed visits. */
    val completedVisits: Int

    /** Whether the completed visit number [visit] (1-based) ended on a bust. */
    fun isBustVisit(visit: Int): Boolean

    fun throwDart(dart: Dart): LegGame

    fun undo(): LegGame
}

internal fun LegSetup.newGame(): LegGame = when (this) {
    is LegSetup.X01 -> X01LegGame(X01Game(config, startingPlayerIndex = startingPlayerIndex))
    is LegSetup.Cricket -> CricketLegGame(CricketGame(config, startingPlayerIndex = startingPlayerIndex))
}

private class X01LegGame(private val game: X01Game) : LegGame {
    override val darts: List<Dart> get() = game.darts
    override val isFinished: Boolean get() = game.state.isFinished
    override val currentPlayerIndex: Int? get() = game.state.currentPlayerIndex
    override val dartsInVisit: Int
        get() = currentPlayerIndex?.let { game.state.players[it].currentTurnDarts.size } ?: 0
    override val completedVisits: Int get() = game.state.completedTurns.size

    override fun isBustVisit(visit: Int): Boolean =
        game.state.completedTurns.getOrNull(visit - 1)?.outcome == TurnOutcome.BUST

    override fun throwDart(dart: Dart): LegGame = X01LegGame(game.throwDart(dart))

    override fun undo(): LegGame = X01LegGame(game.undo())
}

private class CricketLegGame(private val game: CricketGame) : LegGame {
    override val darts: List<Dart> get() = game.darts
    override val isFinished: Boolean get() = game.state.isFinished
    override val currentPlayerIndex: Int? get() = game.state.currentPlayerIndex
    override val dartsInVisit: Int
        get() = currentPlayerIndex?.let { game.state.players[it].currentTurnDarts.size } ?: 0
    override val completedVisits: Int get() = game.state.completedTurns.size

    override fun isBustVisit(visit: Int): Boolean = false

    override fun throwDart(dart: Dart): LegGame = CricketLegGame(game.throwDart(dart))

    override fun undo(): LegGame = CricketLegGame(game.undo())
}
