package io.github.darkcosmos.kosmodarts.history

import io.github.darkcosmos.kosmodarts.engine.player.PlayerListError
import io.github.darkcosmos.kosmodarts.engine.profile.ProfileDates
import io.github.darkcosmos.kosmodarts.engine.profile.ProfileRules
import io.github.darkcosmos.kosmodarts.engine.profile.RenameOutcome

/** A profile as the Players screen lists it (PM2): [lastEndedAt] is `null` when it has no match. */
data class PlayerSummary(
    val id: Long,
    val name: String,
    val nameKey: String,
    val matchCount: Int,
    val lastEndedAt: Long?,
)

/** What a rename request did (PM3). */
sealed interface RenameResult {
    /** The profile now has [name] under [nameKey]. */
    data class Renamed(val name: String, val nameKey: String) : RenameResult

    /** The new name is not valid; nothing changed. */
    data class Invalid(val violation: PlayerListError) : RenameResult

    /** The name key belongs to the profile [targetId]; nothing changed, the screen offers a merge. */
    data class Clash(val targetId: Long) : RenameResult

    /** The profile does not exist (or is the anonymous one, PM7); nothing changed. */
    data object Unknown : RenameResult
}

/** What a merge request did (PM4). */
enum class MergeResult {
    /** Every participation of the source moved to the target and the source is gone. */
    MERGED,

    /** Both profiles played a same match; nothing changed. */
    SHARED_MATCH,

    /** A profile does not exist, is the anonymous one (PM7) or both ids are the same; nothing changed. */
    UNKNOWN,
}

/**
 * The profiles behind the Players screen (PM2 to PM5). Blocking: runs on a persistence queue or a read executor,
 * never on the main thread. Never throws: a database failure leaves everything unchanged and gives
 * [DbResult.Failed] (PM10, ADR 0012, "Failure contract", AR9).
 */
interface PlayersRepository {
    /** PM2: every profile but the anonymous one, most recently used first. */
    fun list(): DbResult<List<PlayerSummary>>

    /** PM3: renames the profile [id] to [name]. */
    fun rename(id: Long, name: String): DbResult<RenameResult>

    /** PM5: the reset date of the profile [id] becomes [at]; nothing else changes. */
    fun reset(id: Long, at: Long): DbResult<Unit>

    /** PM4: merges the profile [sourceId] into [targetId]. */
    fun merge(sourceId: Long, targetId: Long): DbResult<MergeResult>
}

/** The Room implementation: every multi-statement action runs in one transaction (PM10). */
class RoomPlayersRepository(private val database: HistoryDatabase) : PlayersRepository {

    override fun list(): DbResult<List<PlayerSummary>> = dbResult {
        database.playersDao().rows(ProfileRules.ANONYMOUS_NAME_KEY).map {
            PlayerSummary(it.id, it.name, it.nameKey, it.matchCount, it.lastEndedAt)
        }
    }

    override fun rename(id: Long, name: String): DbResult<RenameResult> = dbResult {
        val dao = database.playersDao()
        database.runInTransaction<RenameResult> {
            val profile = dao.find(id)?.takeUnless { it.nameKey == ProfileRules.ANONYMOUS_NAME_KEY }
            if (profile == null) {
                RenameResult.Unknown
            } else {
                val others = dao.all().filter { it.id != id }
                renamed(dao, profile, name, others)
            }
        }
    }

    private fun renamed(
        dao: PlayersDao,
        profile: ProfileEntity,
        name: String,
        others: List<ProfileEntity>,
    ): RenameResult = when (val outcome = ProfileRules.rename(name, profile.nameKey, otherKeys(others))) {
        is RenameOutcome.Invalid -> RenameResult.Invalid(outcome.violation)
        is RenameOutcome.Clash -> RenameResult.Clash(others.first { it.nameKey == outcome.targetKey }.id)
        is RenameOutcome.Renamed -> {
            dao.updateName(profile.id, outcome.name, outcome.key)
            RenameResult.Renamed(outcome.name, outcome.key)
        }
    }

    private fun otherKeys(others: List<ProfileEntity>): Set<String> = others.map { it.nameKey }.toSet()

    override fun reset(id: Long, at: Long): DbResult<Unit> = dbResult {
        val dao = database.playersDao()
        database.runInTransaction {
            val profile = dao.find(id)
            if (profile != null && profile.nameKey != ProfileRules.ANONYMOUS_NAME_KEY) dao.updateResetAt(id, at)
        }
    }

    override fun merge(sourceId: Long, targetId: Long): DbResult<MergeResult> = dbResult {
        val dao = database.playersDao()
        database.runInTransaction<MergeResult> {
            val source = dao.find(sourceId)
            val target = dao.find(targetId)
            when {
                sourceId == targetId || source == null || target == null || isAnonymous(source, target) ->
                    MergeResult.UNKNOWN
                !ProfileRules.canMerge(dao.sessionIdsOf(sourceId).toSet(), dao.sessionIdsOf(targetId).toSet()) ->
                    MergeResult.SHARED_MATCH
                else -> {
                    moveAndDelete(dao, source, target)
                    MergeResult.MERGED
                }
            }
        }
    }

    private fun isAnonymous(source: ProfileEntity, target: ProfileEntity): Boolean =
        ProfileRules.ANONYMOUS_NAME_KEY in setOf(source.nameKey, target.nameKey)

    private fun moveAndDelete(dao: PlayersDao, source: ProfileEntity, target: ProfileEntity) {
        val dates = ProfileRules.mergedDates(source.dates(), target.dates())
        dao.moveParticipations(source.id, target.id)
        dao.updateDates(target.id, dates.createdAt, dates.lastUsedAt, dates.resetAt)
        dao.deleteProfile(source.id)
    }

    private fun ProfileEntity.dates() = ProfileDates(resetAt = resetAt, createdAt = createdAt, lastUsedAt = lastUsedAt)
}
