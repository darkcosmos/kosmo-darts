package io.github.darkcosmos.kosmodarts.history

/** A player profile as the screens need it (PF1): the first spelling of the name, its key and its last use. */
data class Profile(val name: String, val nameKey: String, val lastUsedAt: Long)

/**
 * Profiles of the players (PF1, PF2, PF3, PF5). Blocking: runs on the persistence queue or a read executor, never on
 * the main thread. Never throws: a database failure gives [DbResult.Failed] (ADR 0012, "Failure contract", AR9).
 */
interface ProfileRepository {
    /**
     * Start or Rematch (PF2): creates a profile for each of [names] whose name key matches none, with that spelling,
     * and moves the last use of every one to [at]. An existing profile keeps its first spelling.
     */
    fun touchOrCreate(names: List<String>, at: Long): DbResult<Unit>

    /** Every profile, most recently used first (PF3). */
    fun recent(): DbResult<List<Profile>>
}

/** The Room implementation: every write goes through the one [ProfileDao.upsertUsed] (review A3). */
class RoomProfileRepository(
    private val database: HistoryDatabase,
    private val nameKey: (String) -> String,
) : ProfileRepository {

    override fun touchOrCreate(names: List<String>, at: Long): DbResult<Unit> = dbResult {
        val dao = database.profileDao()
        database.runInTransaction { names.forEach { dao.upsertUsed(it, nameKey(it), at) } }
    }

    override fun recent(): DbResult<List<Profile>> = dbResult {
        database.historyDao().profilesByRecency().map { Profile(it.name, it.nameKey, it.lastUsedAt) }
    }
}
