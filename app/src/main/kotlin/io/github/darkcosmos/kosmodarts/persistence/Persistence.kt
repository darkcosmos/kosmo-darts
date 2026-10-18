package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import java.util.concurrent.CountDownLatch
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
        return task.get()
    }

    /** Saves [saved] in the background. A game over [GameSaveCodec.MAX_DARTS] darts keeps the previous file (R12). */
    fun saveGame(saved: SavedGame) {
        gameCodec.encode(saved)?.let(gameWriter::write)
    }

    fun deleteGame() = gameWriter.delete()

    fun saveLastSetup(config: X01Config) = setupWriter.write(setupCodec.encode(config))

    /** Waits until every read and write requested so far is done. For tests that check the files. */
    fun awaitIdle() {
        val done = CountDownLatch(1)
        executor.execute(done::countDown)
        done.await()
    }
}
