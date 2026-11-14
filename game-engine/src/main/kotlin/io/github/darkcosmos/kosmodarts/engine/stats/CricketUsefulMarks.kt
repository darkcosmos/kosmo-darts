package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketGame
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMarks
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketPlayerState.Companion.CLOSING_MARKS
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/**
 * What one Cricket dart yielded for its thrower (docs/rules/statistics.md, STC1, STC4).
 *
 * @property useful the useful marks of the dart (STC1).
 * @property pointsScored points the dart scored for the thrower in Standard (CR4); 0 otherwise.
 * @property pointsGiven points the dart gave to the opponents in Cut-throat, summed over the opponents (CR5, STC4);
 *   0 otherwise.
 */
data class DartYield(val useful: Int, val pointsScored: Int, val pointsGiven: Int) {
    companion object {
        /** A dart that yields nothing: a miss, a number from 1 to 14, a mark on a dead target. */
        val NONE: DartYield = DartYield(useful = 0, pointsScored = 0, pointsGiven = 0)
    }
}

/**
 * The yields of the darts of a leg, one per dart in throw order.
 *
 * @property yields one entry per dart that the replay accepted, in throw order.
 * @property isValid `false` when the replay rejected a dart (a start player out of range, a dart after the win): then
 *   [yields] stops before the faulty dart and the caller must not trust the leg.
 */
data class CricketYields(val yields: List<DartYield>, val isValid: Boolean)

/**
 * Replays a Cricket leg and returns, for each dart, its useful marks (STC1) and the points it scored or gave (STC4).
 * Built on [CricketGame]: the state before each dart gives the thrower, his marks and the open targets of the others.
 * Never throws: a leg the replay rejects gives [CricketYields.isValid] `false` (CR9: no dart after the win).
 */
fun usefulMarks(config: CricketConfig, darts: List<Dart>, startingPlayerIndex: Int = 0): CricketYields {
    val yields = ArrayList<DartYield>(darts.size)
    var isValid = startingPlayerIndex in config.players.indices
    var game = if (isValid) CricketGame(config, emptyList(), startingPlayerIndex) else null
    for (dart in darts) {
        val thrower = game?.state?.currentPlayerIndex
        if (game == null || thrower == null) {
            isValid = false
            break
        }
        yields += yieldOf(config, game, thrower, dart)
        game = game.throwDart(dart)
    }
    return CricketYields(yields, isValid)
}

/** STC1 and STC4 for [dart] thrown by [thrower] in [game], before the dart. */
private fun yieldOf(config: CricketConfig, game: CricketGame, thrower: Int, dart: Dart): DartYield {
    val marks = CricketMarks.of(dart, config)
    val target = marks.target ?: return DartYield.NONE
    val players = game.state.players
    val closing = minOf(marks.count, CLOSING_MARKS - players[thrower].marksOn(target)).coerceAtLeast(0)
    val surplus = marks.count - closing
    val openOpponents = players.indices.count { it != thrower && !players[it].hasClosed(target) }
    val surplusPoints = if (openOpponents == 0) 0 else surplus * target.points
    val isScoring = surplusPoints > 0
    return DartYield(
        useful = closing + if (isScoring) surplus else 0,
        pointsScored = if (config.variant == CricketVariant.STANDARD) surplusPoints else 0,
        pointsGiven = if (config.variant == CricketVariant.CUT_THROAT) surplusPoints * openOpponents else 0,
    )
}
