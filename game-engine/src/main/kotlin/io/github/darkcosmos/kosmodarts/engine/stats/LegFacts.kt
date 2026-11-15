package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketGame
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config

/** Replay result of a leg (ST10): a rejection reason, or per-dart throwers and facts. */
internal class LegFacts(
    val reason: InvalidReason? = null,
    val throwers: List<Int> = emptyList(),
    val remaining: List<Int>? = null,
    val bust: List<Boolean>? = null,
    val yields: List<DartYield>? = null,
)

/** Replays an X01 leg with the engine and checks it ends with the stored winner (ST10). */
internal fun x01Facts(
    settings: ArchivedSettings.X01,
    names: List<String>,
    leg: RawStatsLeg,
    darts: List<Dart>,
): LegFacts {
    if (settings.startScore !in X01Config.START_SCORES) return LegFacts(InvalidReason.SETTINGS_UNDECODABLE)
    val config = X01Config(names, settings.startScore, settings.checkoutRule)
    val replay = x01Remaining(config, darts, leg.starterPosition)
    return when {
        !replay.isValid -> LegFacts(InvalidReason.DART_REJECTED)
        replay.winnerIndex != leg.winnerPosition -> LegFacts(InvalidReason.LEG_WINNER)
        else -> LegFacts(
            throwers = replay.darts.map { it.thrower },
            remaining = replay.darts.map { it.remainingBefore },
            bust = replay.darts.map { it.isBust },
        )
    }
}

/** Replays a Cricket leg with the engine and checks it ends with the stored winner (ST10). */
internal fun cricketFacts(
    settings: ArchivedSettings.Cricket,
    names: List<String>,
    leg: RawStatsLeg,
    darts: List<Dart>,
): LegFacts {
    val config = CricketConfig(names, settings.cricketVariant, settings.isBullInPlay)
    val yields = usefulMarks(config, darts, leg.starterPosition)
    if (!yields.isValid) return LegFacts(InvalidReason.DART_REJECTED)
    val (throwers, winner) = cricketThrowers(config, darts, leg.starterPosition)
    return if (winner != leg.winnerPosition) {
        LegFacts(InvalidReason.LEG_WINNER)
    } else {
        LegFacts(throwers = throwers, yields = yields.yields)
    }
}

/** The thrower of each dart and the winner of a Cricket leg whose darts the replay already accepted. */
private fun cricketThrowers(config: CricketConfig, darts: List<Dart>, starter: Int): Pair<List<Int>, Int?> {
    val throwers = ArrayList<Int>(darts.size)
    var game = CricketGame(config, emptyList(), starter)
    for (dart in darts) {
        val thrower = game.state.currentPlayerIndex ?: break
        throwers += thrower
        game = game.throwDart(dart)
    }
    return throwers to game.state.winnerIndex
}
