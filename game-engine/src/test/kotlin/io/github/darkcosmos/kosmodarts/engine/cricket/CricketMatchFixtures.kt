package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.dart

internal fun cricketMatch(
    vararg players: String,
    variant: CricketVariant = CricketVariant.STANDARD,
    isBullInPlay: Boolean = true,
    legsPerSet: Int = 1,
    sets: Int = 1,
): CricketMatch = CricketMatch.start(
    CricketMatchConfig(CricketConfig(players.toList(), variant, isBullInPlay), MatchFormat(legsPerSet, sets)),
)

/** Throws each dart of [codes], in order. */
internal fun CricketMatch.throwing(vararg codes: String): CricketMatch =
    codes.fold(this) { match, code -> match.throwDart(dart(code)) }

/** Throws whole turns, one string per turn, darts separated by spaces ("T20 D20 MISS"). */
internal fun CricketMatch.turns(vararg turns: String): CricketMatch =
    throwing(*turns.flatMap { it.split(' ') }.toTypedArray())

/**
 * Plays the current leg until [winner] wins it, without confirming it: [winner] closes each target in play in board
 * order without a surplus mark (a triple per number, then Bull and 25), every other player misses. Nobody scores, so
 * the winner meets CR8 in both variants.
 */
internal fun CricketMatch.playLegWonBy(winner: Int): CricketMatch {
    val closingDarts = config.targets.flatMap(::closingDarts)
    var match = this
    while (!match.currentLeg.state.isFinished) {
        val state = match.currentLeg.state
        val player = checkNotNull(state.currentPlayerIndex)
        val dart = if (player == winner) closingDarts[state.players[player].dartsThrown] else Dart.MISS
        match = match.throwDart(dart)
    }
    return match
}

/** Plays and confirms one leg per winner, in order. */
internal fun CricketMatch.confirmLegsWonBy(vararg winners: Int): CricketMatch =
    winners.fold(this) { match, winner -> match.playLegWonBy(winner).confirmLeg() }

/** Name of the player who starts the current leg. */
internal val CricketMatch.starterName: String
    get() = config.players[currentLeg.startingPlayerIndex]

private fun closingDarts(target: CricketTarget): List<Dart> =
    if (target.isNumber) listOf(Dart.triple(target.points)) else listOf(Dart.BULL, Dart.OUTER_BULL)
