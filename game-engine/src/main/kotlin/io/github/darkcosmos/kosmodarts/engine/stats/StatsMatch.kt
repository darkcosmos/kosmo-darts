package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier

/** A player of an archived match as the app reads it (no rule applied). Positions are 0-based, ranks 1-based. */
data class RawStatsPlayer(val profileId: Long, val position: Int, val rank: Int, val legsWon: Int, val setsWon: Int)

/** A leg of an archived match as the app reads it. [winnerPosition] is the stored winner. */
data class RawStatsLeg(val ordinal: Int, val starterPosition: Int, val winnerPosition: Int)

/**
 * A dart of an archived match as the app reads it.
 *
 * @property multiplier `null` when the stored value is not a known multiplier (ST10).
 * @property isVoided whether the dart was undone (DJ2, ST1).
 */
data class RawStatsDart(
    val legOrdinal: Int,
    val position: Int,
    val visit: Int,
    val rank: Int,
    val segment: Int,
    val multiplier: Multiplier?,
    val isVoided: Boolean,
)

/**
 * An archived match as the app maps it from the database: plain values, no rule applied (ST10). Only
 * [StatsMatchValidator.validate] turns it into a [StatsMatch].
 *
 * @property settings the decoded settings, `null` when the stored text does not decode.
 * @property darts every dart of every leg, voided ones included, ordered by leg, visit and rank.
 */
data class RawStatsMatch(
    val sessionId: String,
    val startedAt: Long,
    val endedAt: Long,
    val settings: ArchivedSettings?,
    val players: List<RawStatsPlayer>,
    val legs: List<RawStatsLeg>,
    val darts: List<RawStatsDart>,
)

/** A player of a validated match. */
data class StatsPlayer(val profileId: Long, val position: Int, val rank: Int, val legsWon: Int, val setsWon: Int)

/**
 * A dart in play of a validated match, with the facts the replay gave for it.
 *
 * @property remainingBefore X01 only: the thrower's remaining score before the dart, mid-visit included; `null` in
 *   Cricket.
 * @property isBust X01 only: whether the dart busted its visit; always `false` in Cricket.
 * @property yield Cricket only: useful marks and points of the dart (STC1, STC4); `null` in X01.
 */
data class StatsDart(
    val position: Int,
    val visit: Int,
    val rank: Int,
    val dart: Dart,
    val remainingBefore: Int?,
    val isBust: Boolean,
    val yield: DartYield?,
)

/** A leg of a validated match: its stored winner and its darts in play, in throw order (voided darts are absent). */
data class StatsLeg(val ordinal: Int, val starterPosition: Int, val winnerPosition: Int, val darts: List<StatsDart>)

/**
 * A match whose data obeys the rules: the only input of the statistics (ST10). The constructor is internal, so the only
 * way to get one is [StatsMatchValidator.validate]. Voided darts never enter it (ST1, DJ2).
 */
class StatsMatch internal constructor(
    val sessionId: String,
    val startedAt: Long,
    val endedAt: Long,
    val settings: ArchivedSettings,
    val players: List<StatsPlayer>,
    val legs: List<StatsLeg>,
) {
    /** Whether the match had a single player: it counts in averages, never in results (ST5). */
    val isSolo: Boolean get() = players.size == 1

    /** The darts in play of the player at [position], over all legs, in leg then throw order (ST1). */
    fun dartsOf(position: Int): List<StatsDart> = legs.flatMap { leg -> leg.darts.filter { it.position == position } }

    /** The player of [profileId], or `null`. */
    fun playerOf(profileId: Long): StatsPlayer? = players.firstOrNull { it.profileId == profileId }
}
