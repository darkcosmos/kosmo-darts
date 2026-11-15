package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/**
 * Builders of valid archived matches for the statistics tests: the player at position 0 wins every leg and the profile
 * id of a player is his position + 1. Each corrupt variant is a `copy` of the valid raw match.
 */
object StatsMatchFixtures {
    private val miss = Dart.MISS

    /** The visits of the winner of a 301 or 501 leg: ends with a double (valid in single out and double out). */
    private fun x01Visits(startScore: Int): List<List<Dart>> {
        val big = List(3) { Dart.triple(20) }
        return if (startScore == 501) {
            listOf(big, big, listOf(Dart.triple(20), Dart.triple(19), Dart.double(12)))
        } else {
            listOf(big, listOf(Dart.triple(20), Dart.triple(19), Dart.double(2)))
        }
    }

    /** The visits of the winner of a Cricket leg: 20 to 15 closed with triples, then the Bull with 50 and 25. */
    private fun cricketVisits(): List<List<Dart>> = listOf(
        listOf(Dart.triple(20), Dart.triple(19), Dart.triple(18)),
        listOf(Dart.triple(17), Dart.triple(16), Dart.triple(15)),
        listOf(Dart.BULL, Dart.OUTER_BULL),
    )

    /** Raw darts of one leg: [visits] of player 0, three misses of every other player between them. */
    private fun legDarts(legOrdinal: Int, players: Int, visits: List<List<Dart>>): List<RawStatsDart> {
        val result = ArrayList<RawStatsDart>()
        var visit = 0
        visits.forEachIndexed { index, darts ->
            visit += 1
            darts.forEachIndexed { rank, dart -> result += raw(legOrdinal, 0, visit, rank + 1, dart) }
            if (index < visits.lastIndex) {
                for (other in 1 until players) {
                    visit += 1
                    repeat(3) { rank -> result += raw(legOrdinal, other, visit, rank + 1, miss) }
                }
            }
        }
        return result
    }

    private fun raw(legOrdinal: Int, position: Int, visit: Int, rank: Int, dart: Dart) =
        RawStatsDart(legOrdinal, position, visit, rank, dart.segment, dart.multiplier, isVoided = false)

    private fun build(
        sessionId: String,
        settings: ArchivedSettings,
        players: Int,
        legs: Int,
        startedAt: Long,
        endedAt: Long,
        visits: List<List<Dart>>,
    ): RawStatsMatch = RawStatsMatch(
        sessionId = sessionId,
        startedAt = startedAt,
        endedAt = endedAt,
        settings = settings,
        players = List(players) {
            RawStatsPlayer(
                profileId = it + 1L,
                position = it,
                rank = if (it == 0) 1 else 2,
                legsWon = if (it == 0) legs else 0,
                setsWon = if (it == 0) 1 else 0,
            )
        },
        legs = List(legs) { RawStatsLeg(it, starterPosition = 0, winnerPosition = 0) },
        darts = (0 until legs).flatMap { legDarts(it, players, visits) },
    )

    /** A valid X01 match of [players] players: player 0 checks out in each of [legs] legs. */
    fun x01Raw(
        players: Int = 2,
        startScore: Int = 301,
        checkoutRule: CheckoutRule = CheckoutRule.DOUBLE_OUT,
        legs: Int = 1,
        sessionId: String = "x01",
        startedAt: Long = 1_000,
        endedAt: Long = 2_000,
    ): RawStatsMatch = build(
        sessionId,
        ArchivedSettings.X01(startScore, checkoutRule, MatchFormat(legsPerSet = legs, sets = 1)),
        players,
        legs,
        startedAt,
        endedAt,
        x01Visits(startScore),
    )

    /** A valid Cricket match of [players] players: player 0 closes every target in each of [legs] legs. */
    fun cricketRaw(
        players: Int = 2,
        variant: CricketVariant = CricketVariant.STANDARD,
        legs: Int = 1,
        sessionId: String = "cricket",
        startedAt: Long = 1_000,
        endedAt: Long = 2_000,
    ): RawStatsMatch = build(
        sessionId,
        ArchivedSettings.Cricket(variant, isBullInPlay = true, MatchFormat(legsPerSet = legs, sets = 1)),
        players,
        legs,
        startedAt,
        endedAt,
        cricketVisits(),
    )

    /** The validated match of [raw]; fails the test when it is rejected. */
    fun valid(raw: RawStatsMatch): StatsMatch =
        (StatsMatchValidator.validate(raw) as StatsValidation.Valid).match

    fun x01(
        players: Int = 2,
        startScore: Int = 301,
        checkoutRule: CheckoutRule = CheckoutRule.DOUBLE_OUT,
        sessionId: String = "x01",
        startedAt: Long = 1_000,
        endedAt: Long = 2_000,
    ): StatsMatch = valid(x01Raw(players, startScore, checkoutRule, 1, sessionId, startedAt, endedAt))

    fun cricket(
        players: Int = 2,
        variant: CricketVariant = CricketVariant.STANDARD,
        sessionId: String = "cricket",
        startedAt: Long = 1_000,
        endedAt: Long = 2_000,
    ): StatsMatch = valid(cricketRaw(players, variant, 1, sessionId, startedAt, endedAt))
}
