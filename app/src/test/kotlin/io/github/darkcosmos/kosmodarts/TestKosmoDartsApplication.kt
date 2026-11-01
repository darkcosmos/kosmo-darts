package io.github.darkcosmos.kosmodarts

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMatch
import io.github.darkcosmos.kosmodarts.history.ArchiveCheck
import io.github.darkcosmos.kosmodarts.history.ArchiveGateway
import io.github.darkcosmos.kosmodarts.history.ArchiveOutcome
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.HistoryFilters
import io.github.darkcosmos.kosmodarts.history.HistoryRepository
import io.github.darkcosmos.kosmodarts.history.MatchArchiveRequest
import io.github.darkcosmos.kosmodarts.persistence.AtomicFileStore
import io.github.darkcosmos.kosmodarts.persistence.Persistence
import io.github.darkcosmos.kosmodarts.ui.history.FakeHistoryRepository
import java.io.File

/**
 * The application of the Robolectric tests (`robolectric.properties`). Its persistence runs every task at once on the
 * calling thread and archives into a gateway that always succeeds: a screen test that confirms a win sees the end
 * screen after the main looper idles, with no real database and no save thread to wait for. Tests of the archive
 * itself build their own [Persistence].
 */
class TestKosmoDartsApplication : KosmoDartsApplication() {

    override fun createPersistence(): Persistence {
        val archive = InstantArchiveGateway()
        return Persistence(
            gameStore = AtomicFileStore(File(filesDir, GAME_FILE)),
            setupStore = AtomicFileStore(File(filesDir, SETUP_FILE)),
            executor = { it.run() },
            archiveRequest = MatchArchiveRequest(archive, "test"),
            archiveCheck = ArchiveCheck(archive),
            history = object : HistoryRepository {
                override fun list(filters: HistoryFilters) = history.list(filters)

                override fun match(sessionId: Long) = history.match(sessionId)
            },
        )
    }

    companion object {
        /** What the History screen of a test reads; a test sets it before it opens History, and puts it back after. */
        @Volatile
        var history: HistoryRepository = FakeHistoryRepository()
    }
}

/** Archives everything, remembers nothing. */
private class InstantArchiveGateway : ArchiveGateway {
    override fun archive(archived: ArchivedMatch, colorsArgb: List<Int>, appVersion: String) =
        DbResult.Ok(ArchiveOutcome.ARCHIVED)

    override fun isArchived(matchId: String) = DbResult.Ok(false)
}
