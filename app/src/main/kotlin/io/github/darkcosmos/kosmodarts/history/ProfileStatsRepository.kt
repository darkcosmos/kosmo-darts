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
import io.github.darkcosmos.kosmodarts.engine.stats.StatsMatches
import io.github.darkcosmos.kosmodarts.engine.stats.commonCounts
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

/** A profile of the picker (HH1): [commonCount] matches in common with the shown profile, 0 included. */
data class PickerProfile(val id: Long, val name: String, val commonCount: Int)

/**
 * The picker of the head-to-head of profile [profileId] (HH1): every other profile except the anonymous one (PM7),
 * most common matches first, then by name.
 */
data class HeadToHeadPicker(val profileId: Long, val name: String, val others: List<PickerProfile>)

/** One side of a head-to-head: the profile and its dates (PM4, PM5). */
data class PairProfile(val id: Long, val name: String, val createdAt: Long, val resetAt: Long)

/**
 * The statistics input of the head-to-head of [a] and [b] (HH2, ST10): the valid matches both played, oldest end first,
 * whatever the reset dates (the engine applies them), and how many of their common matches were ignored.
 */
data class HeadToHeadData(
    val a: PairProfile,
    val b: PairProfile,
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

    /** HH1: the profiles [profileId] can be compared with, with their counts. An unknown or anonymous one fails. */
    fun loadPicker(profileId: Long): DbResult<HeadToHeadPicker>

    /** HH2, ST10: the common matches of [a] and [b], validated. An unknown or anonymous profile, or a == b, fails. */
    fun loadPair(a: Long, b: Long): DbResult<HeadToHeadData>
}

/** The Room implementation: batches of [BATCH_SIZE] sessions, each batch's rows dropped once validated. */
class RoomProfileStatsRepository(private val database: HistoryDatabase) : ProfileStatsRepository {

    override fun load(profileId: Long): DbResult<ProfileStatsData> = dbResult {
        val dao = database.statsDao()
        val profile = realProfile(dao, profileId)
        val matches = validMatches(dao, dao.sessionIds(profileId))
        ProfileStatsData(
            profile.id,
            profile.name,
            profile.createdAt,
            profile.resetAt,
            matches.matches,
            matches.ignoredCount,
        )
    }

    override fun loadPicker(profileId: Long): DbResult<HeadToHeadPicker> = dbResult {
        val dao = database.statsDao()
        val profile = realProfile(dao, profileId)
        val others = dao.profiles().filter { it.id != profileId && it.nameKey != ProfileRules.ANONYMOUS_NAME_KEY }
        val matches = validMatches(dao, dao.sessionIds(profileId)).matches
        val counts = commonCounts(matches, profileId, profile.resetAt, others.associate { it.id to it.resetAt })
        val entries = others.map { PickerProfile(it.id, it.name, counts.getValue(it.id)) }
        HeadToHeadPicker(profileId, profile.name, entries.sortedWith(PICKER_ORDER))
    }

    override fun loadPair(a: Long, b: Long): DbResult<HeadToHeadData> = dbResult {
        val dao = database.statsDao()
        check(a != b) { "A profile is not compared with itself" }
        val first = realProfile(dao, a)
        val second = realProfile(dao, b)
        val matches = validMatches(dao, dao.commonSessionIds(a, b))
        HeadToHeadData(pairProfile(first), pairProfile(second), matches.matches, matches.ignoredCount)
    }

    /** The sessions [ids] in batches of [BATCH_SIZE], each batch's rows dropped once validated (ST10). */
    private fun validMatches(dao: StatsDao, ids: List<Long>): StatsMatches {
        val valid = ArrayList<StatsMatch>()
        var skipped = 0
        ids.chunked(BATCH_SIZE).forEach { batch ->
            val matches = StatsMatchValidator.validateAll(readBatch(dao, batch))
            valid += matches.matches
            skipped += matches.ignoredCount
        }
        return StatsMatches(valid, skipped)
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

        /** HH1: the most common matches first, then the name. */
        val PICKER_ORDER: Comparator<PickerProfile> =
            compareByDescending<PickerProfile> { it.commonCount }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        const val BULL_MULTIPLIER = 2
    }
}

private fun pairProfile(profile: ProfileEntity) =
    PairProfile(profile.id, profile.name, profile.createdAt, profile.resetAt)

/** The profile [id]; an unknown or anonymous (PM7) one is a failure. */
private fun realProfile(dao: StatsDao, id: Long): ProfileEntity {
    val profile = dao.profile(id)
    check(profile != null && profile.nameKey != ProfileRules.ANONYMOUS_NAME_KEY) { "No such profile" }
    return profile
}
