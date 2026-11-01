package io.github.darkcosmos.kosmodarts.persistence

import androidx.annotation.VisibleForTesting
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMatch
import io.github.darkcosmos.kosmodarts.history.ArchiveCheck
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.HistoryFilters
import io.github.darkcosmos.kosmodarts.history.HistoryList
import io.github.darkcosmos.kosmodarts.history.HistoryRepository
import io.github.darkcosmos.kosmodarts.history.MatchArchiveRequest
import io.github.darkcosmos.kosmodarts.history.Profile
import io.github.darkcosmos.kosmodarts.history.ProfileRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask

/**
 * The saved game and the last setup of the app (docs/rules/persistence.md, docs/adr/0002-save-lifecycle.md). One
 * instance per process; every read and write runs on [executor], one at a time, in call order.
 */
class Persistence(
    gameStore: ByteStore,
    setupStore: ByteStore,
    private val executor: Executor,
    private val gameCodec: GameSaveCodec = GameSaveCodec(),
    private val setupCodec: LastSetupCodec = LastSetupCodec(),
    private val profiles: ProfileRepository? = null,
    private val archiveRequest: MatchArchiveRequest? = null,
    private val archiveCheck: ArchiveCheck? = null,
    private val history: HistoryRepository? = null,
    private val readExecutor: Executor = executor,
) {
    private val loader = SaveLoader(gameStore, setupStore, gameCodec, setupCodec)
    private val gameWriter = SaveWriter(gameStore, executor)
    private val setupWriter = SaveWriter(setupStore, executor)

    /**
     * Reads both files and waits for the result, after every write still pending (ADR 0002, decision 3). Called once
     * per ViewModel, on the main thread.
     */
    fun load(): LoadResult {
        val task = FutureTask(::loadAndCheckArchive)
        executor.execute(task)
        return try {
            task.get()
        } catch (ignored: ExecutionException) {
            // Rule R11: a content that breaks the loader in an unforeseen way must not crash every launch. Both files
            // are deleted through the queue, as for any unreadable file (ADR 0002, decision 5).
            deleteGame()
            setupWriter.delete()
            LoadResult(GameLoad.Invalid(InvalidSaveReason.UNEXPECTED_ERROR), lastSetup = null)
        }
    }

    /**
     * AR4: a saved game whose match id the database positively reports as archived (the app stopped between the two
     * steps of AR2) is deleted through the queue and not offered. Any other answer, a database failure included, keeps
     * it (AR9).
     */
    private fun loadAndCheckArchive(): LoadResult {
        val loaded = loader.load()
        val saved = (loaded.game as? GameLoad.Valid)?.saved
        val isArchived = saved != null && archiveCheck?.isSaveArchived(saved.journal.matchId) == true
        if (!isArchived) return loaded
        gameWriter.delete()
        return loaded.copy(game = GameLoad.Absent)
    }

    /**
     * Saves [saved], of any mode, in the background: it replaces the saved game (R20). A game that cannot be written
     * ([GameSaveCodec.encode] gives `null`: over [GameSaveCodec.MAX_DARTS] darts, for instance) keeps the previous
     * file (R12, R24).
     */
    fun saveGame(saved: StoredGame) {
        gameCodec.encode(saved)?.let(gameWriter::write)
    }

    fun deleteGame() = gameWriter.delete()

    fun saveLastSetup(setups: LastSetups) = setupWriter.write(setupCodec.encode(setups))

    /**
     * Creates or touches the profiles of [names] at [at] (PF2), in the background, in call order. A failure (AR9) is
     * ignored: it never blocks a game start. Nothing happens without a profile repository.
     */
    fun touchProfiles(names: List<String>, at: Long) {
        val repository = profiles ?: return
        executor.execute { repository.touchOrCreate(names, at) }
    }

    /**
     * Reads the profiles for the setup suggestions (PF3) on the queue, after every write still pending. [onResult] gets
     * them, most recently used first; a failed read (AR9) or no repository gives an empty list. On the queue thread.
     */
    fun loadProfiles(onResult: (List<Profile>) -> Unit) {
        executor.execute {
            val read = profiles?.recent()
            onResult((read as? DbResult.Ok)?.value.orEmpty())
        }
    }

    /**
     * Reads the History list for [filters] on the read executor (ADR 0012), not on the save queue: a slow read never
     * delays a save. [onResult] gets the list, or [DbResult.Failed] when the read fails or no history is configured
     * (AR9, HS7). On the read thread.
     */
    fun loadHistory(filters: HistoryFilters, onResult: (DbResult<HistoryList>) -> Unit) {
        readExecutor.execute { onResult(history?.list(filters) ?: DbResult.Failed) }
    }

    /**
     * Archives the confirmed match on the queue, then deletes the saved game on the same queue, only if the archive
     * succeeded (AR2). [onResult] gets `true` when the match is archived, `false` otherwise (AR3), on the queue thread.
     * Without an archive configured, the answer is `false` and the saved game stays.
     */
    fun archiveMatch(archived: ArchivedMatch, colorsArgb: List<Int>, onResult: (Boolean) -> Unit) {
        executor.execute {
            val isArchived = archiveRequest?.run(archived, colorsArgb, gameWriter::delete) ?: false
            onResult(isArchived)
        }
    }

    /** Waits until every read and write requested so far is done. For tests that check the files. */
    @VisibleForTesting
    fun awaitIdle() {
        val done = CountDownLatch(1)
        executor.execute(done::countDown)
        done.await()
    }
}
