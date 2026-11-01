package io.github.darkcosmos.kosmodarts.history

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMatch

/**
 * What the app asks of the archive database (AR2, AR4). Blocking: both calls run on the persistence queue. Neither
 * throws: a database that fails answers [DbResult.Failed] (AR9, ADR 0012 "Failure contract"). The seam lets tests
 * replace the database with a fake that fails.
 */
interface ArchiveGateway {
    /** Writes [archived] with one ARGB color per player, or answers that its match id was already archived (AR4). */
    fun archive(archived: ArchivedMatch, colorsArgb: List<Int>, appVersion: String): DbResult<ArchiveOutcome>

    /** Whether [matchId] is archived. */
    fun isArchived(matchId: String): DbResult<Boolean>
}
