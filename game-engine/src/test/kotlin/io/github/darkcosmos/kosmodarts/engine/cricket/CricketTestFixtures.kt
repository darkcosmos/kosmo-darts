package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.x01.dart

// Turn shortcuts for the Cricket tests, in the X01 dart notation ("T20", "D19", "20", "25", "BULL", "MISS").

/** A turn of three misses. */
internal const val MISSES = "MISS MISS MISS"

/** A turn that closes 20, 19 and 18 from no mark, without surplus. */
internal const val CLOSE_20_19_18 = "T20 T19 T18"

/** A turn that closes 17, 16 and 15 from no mark, without surplus. */
internal const val CLOSE_17_16_15 = "T17 T16 T15"

/** A turn that closes the Bull from no mark with its first two darts (2 + 1 marks), without surplus. */
internal const val CLOSE_BULL = "BULL 25 MISS"

internal fun cricketGame(
    vararg players: String = arrayOf("Anna"),
    variant: CricketVariant = CricketVariant.STANDARD,
    isBullInPlay: Boolean = true,
    startingPlayerIndex: Int = 0,
): CricketGame =
    CricketGame(CricketConfig(players.toList(), variant, isBullInPlay), startingPlayerIndex = startingPlayerIndex)

/** Throws each dart of [codes], in order. */
internal fun CricketGame.throwing(vararg codes: String): CricketGame =
    codes.fold(this) { game, code -> game.throwDart(dart(code)) }

/** Throws whole turns, one string per turn, darts separated by spaces ("T20 D20 MISS"). */
internal fun CricketGame.turns(vararg turns: String): CricketGame =
    throwing(*turns.flatMap { it.split(' ') }.toTypedArray())

internal fun CricketGame.player(index: Int): CricketPlayerState = state.players[index]

internal fun CricketGame.points(): List<Int> = state.players.map(CricketPlayerState::points)

internal fun CricketGame.ranks(): List<Pair<Int, Int>> = state.ranking.map { it.rank to it.playerIndex }
