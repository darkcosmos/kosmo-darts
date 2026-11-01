package io.github.darkcosmos.kosmodarts.persistence

import androidx.annotation.VisibleForTesting
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
) {
    private val loader = SaveLoader(gameStore, setupStore, gameCodec, setupCodec)
    private val gameWriter = SaveWriter(gameStore, executor)
    private val setupWriter = SaveWriter(setupStore, executor)

    /**
     * Reads both files and waits for the result, after every write still pending (ADR 0002, decision 3). Called once
     * per ViewModel, on the main thread.
     */
    fun load(): LoadResult {
        val task = FutureTask(loader::load)
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

    /** Waits until every read and write requested so far is done. For tests that check the files. */
    @VisibleForTesting
    fun awaitIdle() {
        val done = CountDownLatch(1)
        executor.execute(done::countDown)
        done.await()
    }
}
