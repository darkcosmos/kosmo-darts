package io.github.darkcosmos.kosmodarts.history

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMatch

/**
 * An [ArchiveGateway] that answers what the test says and records what it was asked, without any database. Its calls
 * are made on the test thread when a test drains the persistence queue.
 */
internal class FakeArchiveGateway(
    var archiveAnswer: DbResult<ArchiveOutcome> = DbResult.Ok(ArchiveOutcome.ARCHIVED),
    var archivedIds: DbResult<Set<String>> = DbResult.Ok(emptySet()),
) : ArchiveGateway {

    /** When set, [archive] waits for it: a test or a capture holds the archive "in progress". */
    @Volatile var gate: java.util.concurrent.CountDownLatch? = null

    /** The matches received by [archive], in order, with their colors. */
    val archived: MutableList<Pair<ArchivedMatch, List<Int>>> = mutableListOf()

    /** The versions received by [archive]. */
    val versions: MutableList<String> = mutableListOf()

    /** The ids asked to [isArchived]. */
    val checked: MutableList<String> = mutableListOf()

    /** What happened, in order: "archive" and "check". Lets a test prove an order. */
    val calls: MutableList<String> = mutableListOf()

    override fun archive(archived: ArchivedMatch, colorsArgb: List<Int>, appVersion: String): DbResult<ArchiveOutcome> {
        gate?.await()
        calls += "archive"
        this.archived += archived to colorsArgb
        versions += appVersion
        return archiveAnswer
    }

    override fun isArchived(matchId: String): DbResult<Boolean> {
        calls += "check"
        checked += matchId
        return when (val ids = archivedIds) {
            is DbResult.Ok -> DbResult.Ok(matchId in ids.value)
            DbResult.Failed -> DbResult.Failed
        }
    }
}
