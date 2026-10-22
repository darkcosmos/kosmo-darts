package io.github.darkcosmos.kosmodarts.engine.x01

import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat

internal fun newMatch(
    vararg players: String,
    legsPerSet: Int = 1,
    sets: Int = 1,
    startScore: Int = 301,
    checkoutRule: CheckoutRule = CheckoutRule.SINGLE_OUT,
): X01Match = X01Match.start(
    X01MatchConfig(X01Config(players.toList(), startScore, checkoutRule), MatchFormat(legsPerSet, sets)),
)

internal fun X01Match.throwing(vararg codes: String): X01Match =
    codes.fold(this) { match, code -> match.throwDart(dart(code)) }

/**
 * Plays the current leg until [winner] checks out, without confirming it: [winner] aims at the finish, every other
 * player misses. Never busts, in single and double out.
 */
internal fun X01Match.playLegWonBy(winner: Int): X01Match {
    var match = this
    while (!match.currentLeg.state.isFinished) {
        val state = match.currentLeg.state
        val player = checkNotNull(state.currentPlayerIndex)
        val remaining = state.players[player].remaining
        match = match.throwDart(if (player == winner) finishingDart(remaining, config.checkoutRule) else Dart.MISS)
    }
    return match
}

/** Plays and confirms one leg per winner, in order. */
internal fun X01Match.confirmLegsWonBy(vararg winners: Int): X01Match =
    winners.fold(this) { match, winner -> match.playLegWonBy(winner).confirmLeg() }

private fun finishingDart(remaining: Int, rule: CheckoutRule): Dart = when (rule) {
    CheckoutRule.SINGLE_OUT -> when {
        remaining >= 60 -> Dart.triple(20)
        remaining == 50 -> Dart.BULL
        else -> Dart.single(minOf(20, remaining))
    }
    CheckoutRule.DOUBLE_OUT -> when {
        remaining % 2 == 0 && remaining <= 40 -> Dart.double(remaining / 2)
        remaining == 50 -> Dart.BULL
        remaining >= 62 -> Dart.triple(20)
        remaining % 2 == 1 -> Dart.single(1)
        else -> Dart.single(remaining - 40)
    }
}

/** Name of the player who starts the current leg. */
internal val X01Match.starterName: String
    get() = config.players[currentLeg.startingPlayerIndex]
