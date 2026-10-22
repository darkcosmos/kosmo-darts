package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.settings.inMemoryAudioSettings
import io.github.darkcosmos.kosmodarts.persistence.GameLoad
import io.github.darkcosmos.kosmodarts.persistence.InvalidSaveReason
import io.github.darkcosmos.kosmodarts.persistence.ManualExecutor
import io.github.darkcosmos.kosmodarts.persistence.MemoryStore
import io.github.darkcosmos.kosmodarts.persistence.Persistence
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.SaveWriter
import io.github.darkcosmos.kosmodarts.ui.game.toGameScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Every deletion of the saved game goes through the same queue as the writes, so a write queued before a deletion
 * never recreates the file after it (rules R3, R6, R9, R12; owner request on issue #30). Each case: a write is queued,
 * then the deletion, then the queue is drained: no file is left.
 */
class DeletionOrderTest {

    private val executor = ManualExecutor()
    private val gameStore = MemoryStore()
    private val persistence = Persistence(gameStore, MemoryStore(), executor)

    private fun viewModel(initial: AppState) =
        KosmoDartsViewModel(Random(0), initial, inMemoryAudioSettings()) { persistence.applyEffects(it) }

    @Test
    fun `R6 a dart then Finish game leaves no file`() {
        val won = SaveFixtures.WAITING_CHECKOUT.game
        val beforeWin = SaveFixtures.savedAfter(won.config, won.darts.dropLast(1)).toGameScreenState()
        val viewModel = viewModel(AppState(screen = Screen.Game(beforeWin)))

        viewModel.onEvent(AppEvent.NumberTapped(1)) // The winning dart: a write is queued.
        assertTrue((viewModel.state.value.screen as Screen.Game).game.isCheckoutPending)
        viewModel.onEvent(AppEvent.FinishGameTapped)
        executor.runAll()

        assertNull(gameStore.content)
    }

    @Test
    fun `R6 a dart then Abandon leaves no file`() {
        val viewModel = viewModel(AppState(screen = Screen.Game(SaveFixtures.MID_TURN.toGameScreenState())))

        viewModel.onEvent(AppEvent.MissTapped)
        viewModel.onEvent(AppEvent.Back)
        viewModel.onEvent(AppEvent.AbandonConfirmed)
        executor.runAll()

        assertNull(gameStore.content)
    }

    @Test
    fun `R3 a pending write then Delete in the new-game dialog leaves no file`() {
        persistence.saveGame(SaveFixtures.MID_TURN)
        val viewModel = viewModel(AppState(screen = Screen.Home(resumableGame = SaveFixtures.MID_TURN)))

        viewModel.onEvent(AppEvent.NewGame)
        viewModel.onEvent(AppEvent.NewGameConfirmed)
        executor.runAll()

        assertNull(gameStore.content)
    }

    /** R9: the deletion of an unreadable file runs on the save thread, after the writes queued before it. */
    @Test
    fun `R9 a pending write then an unreadable load leaves no file`() {
        val saveThread = Executors.newSingleThreadExecutor()
        try {
            val store = MemoryStore()
            val threaded = Persistence(store, MemoryStore(), saveThread)
            val gate = CountDownLatch(1)
            saveThread.execute { gate.await() }
            // Queued before the load: unreadable content, written only once the gate opens.
            SaveWriter(store, saveThread).write("not a save".toByteArray())
            gate.countDown()

            assertEquals(GameLoad.Invalid(InvalidSaveReason.MALFORMED), threaded.load().game)
            threaded.awaitIdle()
            assertNull(store.content)
        } finally {
            saveThread.shutdown()
            saveThread.awaitTermination(1, TimeUnit.SECONDS)
        }
    }
}
