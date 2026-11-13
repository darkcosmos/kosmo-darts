package io.github.darkcosmos.kosmodarts.history

/**
 * In-memory [PlayersRepository] for the Players state holder tests: serves [players], answers each action with the
 * configured result and records the calls. A [DbResult.Failed] value simulates a database error (PM10).
 */
class FakePlayersRepository(
    var players: DbResult<List<PlayerSummary>> = DbResult.Ok(emptyList()),
    var renameResult: DbResult<RenameResult> = DbResult.Ok(RenameResult.Unknown),
    var resetResult: DbResult<Unit> = DbResult.Ok(Unit),
    var mergeResult: DbResult<MergeResult> = DbResult.Ok(MergeResult.MERGED),
) : PlayersRepository {
    val renames = mutableListOf<Pair<Long, String>>()
    val resets = mutableListOf<Pair<Long, Long>>()
    val merges = mutableListOf<Pair<Long, Long>>()

    override fun list(): DbResult<List<PlayerSummary>> = players

    override fun rename(id: Long, name: String): DbResult<RenameResult> {
        renames += id to name
        return renameResult
    }

    override fun reset(id: Long, at: Long): DbResult<Unit> {
        resets += id to at
        return resetResult
    }

    override fun merge(sourceId: Long, targetId: Long): DbResult<MergeResult> {
        merges += sourceId to targetId
        return mergeResult
    }
}
