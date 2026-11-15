package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game

/**
 * What the replay of an X01 leg says about one dart.
 *
 * @property thrower index of the player who threw the dart.
 * @property remainingBefore the thrower's remaining score before the dart, mid-visit included (STX3).
 * @property isBust whether the dart busted its visit (STX1, STX6): the score then goes back to the start of the visit.
 */
data class X01DartFacts(val thrower: Int, val remainingBefore: Int, val isBust: Boolean)

/**
 * The facts of the darts of an X01 leg, one per dart in throw order.
 *
 * @property darts one entry per dart that the replay accepted.
 * @property winnerIndex the player who checked out, `null` when the leg is not won.
 * @property isValid `false` when the replay rejected a dart (a start player out of range, a dart after the win): then
 *   [darts] stops before the faulty dart.
 */
data class X01Remaining(val darts: List<X01DartFacts>, val winnerIndex: Int?, val isValid: Boolean)

/**
 * Replays an X01 leg with [X01Game] and gives, for each dart, its thrower, the remaining score before it and whether
 * it busted. Never throws: a leg the replay rejects gives [X01Remaining.isValid] `false`.
 */
fun x01Remaining(config: X01Config, darts: List<Dart>, startingPlayerIndex: Int = 0): X01Remaining {
    val facts = ArrayList<X01DartFacts>(darts.size)
    if (startingPlayerIndex !in config.players.indices) return X01Remaining(facts, null, isValid = false)
    var game = X01Game(config, emptyList(), startingPlayerIndex)
    var isValid = true
    for (dart in darts) {
        val thrower = game.state.currentPlayerIndex
        if (thrower == null) {
            isValid = false
            break
        }
        val before = game.state.players[thrower].remaining
        game = game.throwDart(dart)
        val after = game.state.players[thrower].remaining
        facts += X01DartFacts(thrower, before, isBust = dart.value > 0 && after >= before)
    }
    return X01Remaining(facts, game.state.winnerIndex, isValid)
}
