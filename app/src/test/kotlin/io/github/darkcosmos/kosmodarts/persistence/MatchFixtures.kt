package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig

/** Matches used by the app tests (docs/rules/match.md). Names are made up. */
internal object MatchFixtures {

    /** A new match, 301 single out by default so that legs are short. */
    fun match(
        vararg players: String,
        legsPerSet: Int = 1,
        sets: Int = 1,
        startScore: Int = 301,
        rule: CheckoutRule = CheckoutRule.SINGLE_OUT,
        colorSlots: List<ColorSlot> = ColorSlot.byPosition(players.size),
    ): X01Match = X01Match.start(
        X01MatchConfig(X01Config(players.toList(), startScore, rule), MatchFormat(legsPerSet, sets), colorSlots),
    )

    /** The darts that play the current leg of [match] until [winner] checks out; the others miss. Never a bust. */
    fun dartsToWin(match: X01Match, winner: Int): List<Dart> {
        val darts = mutableListOf<Dart>()
        var current = match
        while (!current.currentLeg.state.isFinished) {
            val state = current.currentLeg.state
            val player = checkNotNull(state.currentPlayerIndex)
            val remaining = state.players[player].remaining
            val dart = if (player == winner) finishing(remaining, match.config.checkoutRule) else Dart.MISS
            darts += dart
            current = current.throwDart(dart)
        }
        return darts
    }

    private fun finishing(remaining: Int, rule: CheckoutRule): Dart = when (rule) {
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
}

/** Plays the current leg until [winner] checks out, without confirming it. */
internal fun X01Match.playLegWonBy(winner: Int): X01Match =
    MatchFixtures.dartsToWin(this, winner).fold(this) { match, dart -> match.throwDart(dart) }

/** Plays and confirms one leg per winner, in order. */
internal fun X01Match.confirmLegsWonBy(vararg winners: Int): X01Match =
    winners.fold(this) { match, winner -> match.playLegWonBy(winner).confirmLeg() }

internal fun X01Match.throwing(vararg darts: Dart): X01Match = darts.fold(this) { match, dart -> match.throwDart(dart) }
