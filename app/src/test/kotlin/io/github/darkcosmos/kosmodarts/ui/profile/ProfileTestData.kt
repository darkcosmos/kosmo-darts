package io.github.darkcosmos.kosmodarts.ui.profile

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.stats.RawStatsDart
import io.github.darkcosmos.kosmodarts.engine.stats.RawStatsLeg
import io.github.darkcosmos.kosmodarts.engine.stats.RawStatsMatch
import io.github.darkcosmos.kosmodarts.engine.stats.RawStatsPlayer
import io.github.darkcosmos.kosmodarts.engine.stats.StatsMatch
import io.github.darkcosmos.kosmodarts.engine.stats.StatsMatchValidator
import io.github.darkcosmos.kosmodarts.engine.stats.StatsValidation
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.history.ProfileStatsData

/** Valid archived matches for the Profile tests: player 1 (position 0) wins the only leg against player 2. */
internal object ProfileTestData {
    const val PROFILE_ID = 1L
    const val CREATED_AT = 1_000L

    private val x01Visits = listOf(
        List(3) { Dart.triple(20) },
        listOf(Dart.triple(20), Dart.triple(19), Dart.double(2)),
    )
    private val cricketVisits = listOf(
        listOf(Dart.triple(20), Dart.triple(19), Dart.triple(18)),
        listOf(Dart.triple(17), Dart.triple(16), Dart.triple(15)),
        listOf(Dart.BULL, Dart.OUTER_BULL),
    )

    private fun raw(sessionId: String, startedAt: Long, endedAt: Long, settings: ArchivedSettings): RawStatsMatch {
        val visits = if (settings is ArchivedSettings.X01) x01Visits else cricketVisits
        var visit = 0
        val darts = ArrayList<RawStatsDart>()
        visits.forEachIndexed { index, throws ->
            visit += 1
            throws.forEachIndexed { rank, dart ->
                darts += RawStatsDart(0, 0, visit, rank + 1, dart.segment, dart.multiplier, isVoided = false)
            }
            if (index < visits.lastIndex) {
                visit += 1
                repeat(3) { rank ->
                    darts += RawStatsDart(0, 1, visit, rank + 1, Dart.MISS.segment, Dart.MISS.multiplier, false)
                }
            }
        }
        return RawStatsMatch(
            sessionId = sessionId,
            startedAt = startedAt,
            endedAt = endedAt,
            settings = settings,
            players = listOf(RawStatsPlayer(1, 0, 1, 1, 1), RawStatsPlayer(2, 1, 2, 0, 0)),
            legs = listOf(RawStatsLeg(0, 0, 0)),
            darts = darts,
        )
    }

    private val single = MatchFormat(legsPerSet = 1, sets = 1)

    fun x01Raw(sessionId: String = "x01", startedAt: Long = 2_000, endedAt: Long = 3_000): RawStatsMatch =
        raw(sessionId, startedAt, endedAt, ArchivedSettings.X01(301, CheckoutRule.DOUBLE_OUT, single))

    fun cricketRaw(sessionId: String = "cricket", startedAt: Long = 2_000, endedAt: Long = 3_000): RawStatsMatch =
        raw(sessionId, startedAt, endedAt, ArchivedSettings.Cricket(CricketVariant.STANDARD, true, single))

    /** A raw match the validator rejects (ST10): a position out of range for the stored players. */
    fun corruptRaw(sessionId: String): RawStatsMatch =
        x01Raw(sessionId).let { it.copy(legs = listOf(RawStatsLeg(0, 0, 5))) }

    fun valid(raw: RawStatsMatch): StatsMatch = (StatsMatchValidator.validate(raw) as StatsValidation.Valid).match

    /** The statistics input as the repository gives it: [raws] go through the real validator (ST10). */
    fun data(
        raws: List<RawStatsMatch>,
        resetAt: Long = CREATED_AT,
        name: String = "Kevin",
    ): ProfileStatsData {
        val validated = StatsMatchValidator.validateAll(raws)
        return ProfileStatsData(PROFILE_ID, name, CREATED_AT, resetAt, validated.matches, validated.ignoredCount)
    }
}
