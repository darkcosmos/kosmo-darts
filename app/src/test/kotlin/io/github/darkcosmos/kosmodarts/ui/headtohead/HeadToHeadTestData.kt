package io.github.darkcosmos.kosmodarts.ui.headtohead

import io.github.darkcosmos.kosmodarts.engine.stats.RawStatsMatch
import io.github.darkcosmos.kosmodarts.engine.stats.RawStatsPlayer
import io.github.darkcosmos.kosmodarts.engine.stats.StatsMatchValidator
import io.github.darkcosmos.kosmodarts.history.HeadToHeadData
import io.github.darkcosmos.kosmodarts.history.PairProfile
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileTestData

/** Valid archived matches of the profiles [A] and [B] (HH2): by default A (position 0) wins the only leg. */
internal object HeadToHeadTestData {
    const val A = 1L
    const val B = 2L
    const val CREATED_AT = 1_000L

    /** An X01 match of A and B: [rankA] and [rankB] are the final ranks, [legsA] and [legsB] the legs won. */
    fun x01(
        sessionId: String,
        startedAt: Long = 2_000,
        rankA: Int = 1,
        rankB: Int = 2,
        legsA: Int = 1,
        legsB: Int = 0,
    ): RawStatsMatch =
        ProfileTestData.x01Raw(sessionId, startedAt, startedAt + 1_000).withPlayers(rankA, rankB, legsA, legsB)

    fun cricket(sessionId: String, startedAt: Long = 2_000): RawStatsMatch =
        ProfileTestData.cricketRaw(sessionId, startedAt, startedAt + 1_000).withPlayers(1, 2, 1, 0)

    private fun RawStatsMatch.withPlayers(rankA: Int, rankB: Int, legsA: Int, legsB: Int) = copy(
        players = listOf(RawStatsPlayer(A, 0, rankA, legsA, legsA), RawStatsPlayer(B, 1, rankB, legsB, legsB)),
    )

    /** The pair as the repository gives it: [raws] go through the real validator (ST10). */
    fun pair(
        raws: List<RawStatsMatch>,
        resetA: Long = CREATED_AT,
        resetB: Long = CREATED_AT,
    ): HeadToHeadData {
        val validated = StatsMatchValidator.validateAll(raws)
        return HeadToHeadData(
            PairProfile(A, "Kevin", CREATED_AT, resetA),
            PairProfile(B, "Zoe", CREATED_AT, resetB),
            validated.matches,
            validated.ignoredCount,
        )
    }
}
