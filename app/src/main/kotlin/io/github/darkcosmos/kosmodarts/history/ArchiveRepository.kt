package io.github.darkcosmos.kosmodarts.history

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMatch

/** What an archive did (AR4). */
enum class ArchiveOutcome {
    /** The match was written. */
    ARCHIVED,

    /** The match id was already archived; nothing was written. */
    ALREADY_ARCHIVED,
}

/**
 * Writes a confirmed match to the archive database (AR2, AR4, AR5, PF2). Blocking: runs on the persistence queue,
 * never on the main thread (ADR 0012).
 *
 * @param nameKey the profile name key (PF1); the app passes the setup's duplicate key (plan D12).
 * @param archiveDao the archive writes; tests may wrap it to inject a failure.
 */
class ArchiveRepository(
    private val database: HistoryDatabase,
    private val nameKey: (String) -> String,
    private val archiveDao: ArchiveDao = database.archiveDao(),
) {

    /**
     * Archives [archived] in one transaction (AR2): when its match id is already archived, returns
     * [ArchiveOutcome.ALREADY_ARCHIVED] without writing (AR4); otherwise upserts each player's profile by name key
     * at the match start (PF2), then inserts the session, the match, the players (colors from [colorsArgb], by
     * position), the legs and the darts. Never throws: any database failure, or [colorsArgb] not giving one color
     * per player, gives [DbResult.Failed] and writes nothing (ADR 0012, "Failure contract").
     *
     * @param appVersion the build's version name (AR5, D31).
     */
    fun archive(archived: ArchivedMatch, colorsArgb: List<Int>, appVersion: String): DbResult<ArchiveOutcome> {
        if (colorsArgb.size != archived.players.size) return DbResult.Failed
        return dbResult {
            database.runInTransaction<ArchiveOutcome> { write(archived, colorsArgb, appVersion) }
        }
    }

    private fun write(archived: ArchivedMatch, colorsArgb: List<Int>, appVersion: String): ArchiveOutcome {
        if (archiveDao.isArchived(archived.matchId)) return ArchiveOutcome.ALREADY_ARCHIVED
        val profileDao = database.profileDao()
        val profileIds = archived.players.map { profileDao.upsertUsed(it.name, nameKey(it.name), archived.startedAt) }
        val sessionId = archiveDao.insertSession(ArchiveMapper.session(archived, appVersion))
        archiveDao.insertMatchRecord(ArchiveMapper.matchRecord(archived, sessionId))
        archiveDao.insertPlayers(
            archived.players.mapIndexed { index, player ->
                ArchiveMapper.player(player, sessionId, profileIds[index], colorsArgb[index])
            },
        )
        val legIds = archived.legs.associate { it.ordinal to archiveDao.insertLeg(ArchiveMapper.leg(it, sessionId)) }
        archiveDao.insertDarts(
            archived.darts.map { ArchiveMapper.dart(it, checkNotNull(legIds[it.legOrdinal]) { "Dart without leg" }) },
        )
        return ArchiveOutcome.ARCHIVED
    }
}
