package io.github.darkcosmos.kosmodarts.history

/** What the launch check found for a saved match id (AR4). */
enum class ArchiveCheckResult {
    /** The database positively reports the match as archived. */
    ARCHIVED,

    /** The database answered that the match is not archived. */
    NOT_ARCHIVED,

    /** The database could not answer (AR9). */
    FAILED,
}

/** The launch check of AR4: has the match of the saved game already been archived? Runs on the persistence queue. */
class ArchiveCheck(private val gateway: ArchiveGateway) {

    fun check(matchId: String): ArchiveCheckResult = when (val answer = gateway.isArchived(matchId)) {
        is DbResult.Ok -> if (answer.value) ArchiveCheckResult.ARCHIVED else ArchiveCheckResult.NOT_ARCHIVED
        DbResult.Failed -> ArchiveCheckResult.FAILED
    }

    /** Only a positive answer deletes the saved game; a failure keeps it and offers it as usual (AR4, AR9, R2). */
    fun isSaveArchived(matchId: String): Boolean = check(matchId) == ArchiveCheckResult.ARCHIVED
}
