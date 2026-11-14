package io.github.darkcosmos.kosmodarts.history

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.profile.ProfileRules
import io.github.darkcosmos.kosmodarts.engine.stats.RawStatsDart
import io.github.darkcosmos.kosmodarts.engine.stats.RawStatsLeg
import io.github.darkcosmos.kosmodarts.engine.stats.RawStatsMatch
import io.github.darkcosmos.kosmodarts.engine.stats.RawStatsPlayer
import io.github.darkcosmos.kosmodarts.engine.stats.StatsMatch
import io.github.darkcosmos.kosmodarts.engine.stats.StatsMatchValidator
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier

/**
 * The statistics input of one profile (ST10): its valid matches, oldest end first, and how many archived matches were
 * ignored because their data breaks the rules. [createdAt] and [resetAt] are the profile's dates (PM4, PM5).
 */
data class ProfileStatsData(
    val profileId: Long,
    val name: String,
    val createdAt: Long,
    val resetAt: Long,
    val matches: List<StatsMatch>,
    val skippedMatches: Int,
)

/**
 * Loads the statistics input of a profile. Blocking: runs on a read executor, never on the main thread. Never throws:
 * a database failure gives [DbResult.Failed] (AR9, HS7), never a skipped count.
 */
interface ProfileStatsRepository {
    /** Every archived match of [profileId], validated (ST10). An unknown or anonymous (PM7) profile gives a failure. */
    fun load(profileId: Long): DbResult<ProfileStatsData>
}

/** The Room implementation: batches of [BATCH_SIZE] sessions, each batch's rows dropped once validated. */
class RoomProfileStatsRepository(private val database: HistoryDatabase) : ProfileStatsRepository {

    override fun load(profileId: Long): DbResult<ProfileStatsData> = dbResult {
        val dao = database.statsDao()
        val profile = dao.profile(profileId)
        check(profile != null && profile.nameKey != ProfileRules.ANONYMOUS_NAME_KEY) { "No such profile" }
        val valid = ArrayList<StatsMatch>()
        var skipped = 0
        dao.sessionIds(profileId).chunked(BATCH_SIZE).forEach { batch ->
            val matches = StatsMatchValidator.validateAll(readBatch(dao, batch))
            valid += matches.matches
            skipped += matches.ignoredCount
        }
        ProfileStatsData(profile.id, profile.name, profile.createdAt, profile.resetAt, valid, skipped)
    }

    private fun readBatch(dao: StatsDao, sessionIds: List<Long>): List<RawStatsMatch> {
        val players = dao.players(sessionIds).groupBy { it.sessionId }
        val legs = dao.legs(sessionIds).groupBy { it.sessionId }
        val darts = dao.darts(sessionIds).groupBy { it.sessionId }
        val bySession = dao.sessions(sessionIds).associateBy { it.sessionId }
        return sessionIds.mapNotNull { id ->
            bySession[id]?.let { session ->
                RawStatsMatch(
                    sessionId = id.toString(),
                    startedAt = session.startedAt,
                    endedAt = session.endedAt,
                    settings = decode(session.settings, session.mode),
                    players = players[id].orEmpty().map(::rawPlayer),
                    legs = legs[id].orEmpty().map { RawStatsLeg(it.ordinal, it.starterPosition, it.winnerPosition) },
                    darts = darts[id].orEmpty().map(::rawDart),
                )
            }
        }
    }

    private fun rawPlayer(row: MatchPlayerEntity) =
        RawStatsPlayer(row.profileId, row.position, row.finalRank, row.legsWon, row.setsWon)

    private fun rawDart(row: StatsDartRow) = RawStatsDart(
        legOrdinal = row.legOrdinal,
        position = row.playerPosition,
        visit = row.visit,
        rank = row.rank,
        segment = engineSegment(row),
        multiplier = engineMultiplier(row),
        isVoided = row.voided,
    )

    /** The stored bull (segment 25, multiplier 2) is the engine's segment 50; a stored 50 is not a stored value. */
    private fun isStoredBull(row: StatsDartRow): Boolean =
        row.segment == Dart.OUTER_BULL_SEGMENT && row.multiplier == BULL_MULTIPLIER

    private fun engineSegment(row: StatsDartRow): Int = if (isStoredBull(row)) Dart.BULL_SEGMENT else row.segment

    /** `null` for an unknown value (ST10): a factor that is no multiplier, or a stored segment 50. */
    private fun engineMultiplier(row: StatsDartRow): Multiplier? = when {
        isStoredBull(row) -> Multiplier.SINGLE
        row.segment == Dart.BULL_SEGMENT -> null
        else -> Multiplier.entries.firstOrNull { it.factor == row.multiplier }
    }

    /** The settings of a stored text, or `null` when it does not decode (ST10): never an exception. */
    private fun decode(text: String, mode: ArchivedMode): ArchivedSettings? = try {
        SettingsText.decode(text, mode)
    } catch (ignored: IllegalStateException) {
        null
    }

    private companion object {
        const val BATCH_SIZE = 100
        const val BULL_MULTIPLIER = 2
    }
}
