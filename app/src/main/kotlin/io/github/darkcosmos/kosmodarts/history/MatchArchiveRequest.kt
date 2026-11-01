package io.github.darkcosmos.kosmodarts.history

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMatch

/**
 * The two steps of the confirmed win (AR2): write the match to the archive, then, and only then, delete the saved game.
 * Runs on the persistence queue; `Persistence` only sequences it.
 *
 * @param appVersion the build's version name (AR5), never null (D31).
 */
class MatchArchiveRequest(private val gateway: ArchiveGateway, private val appVersion: String) {

    /**
     * Archives [archived]; calls [deleteSave] only when the match was written or was already archived (AR4). Returns
     * whether the match is archived: `false` keeps the saved game for a retry (AR3).
     */
    fun run(archived: ArchivedMatch, colorsArgb: List<Int>, deleteSave: () -> Unit): Boolean =
        when (gateway.archive(archived, colorsArgb, appVersion)) {
            is DbResult.Ok -> {
                deleteSave()
                true
            }
            DbResult.Failed -> false
        }
}
