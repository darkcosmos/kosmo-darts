package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.persistence.playLegWonBy
import io.github.darkcosmos.kosmodarts.testing.TEST_STAMP
import io.github.darkcosmos.kosmodarts.testing.cricketGameScreen
import io.github.darkcosmos.kosmodarts.testing.gameScreen
import io.github.darkcosmos.kosmodarts.testing.reduce
import io.github.darkcosmos.kosmodarts.testing.startedJournal
import io.github.darkcosmos.kosmodarts.ui.cricket.CricketGameScreenState
import io.github.darkcosmos.kosmodarts.ui.game.ArchiveStatus
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState
import io.github.darkcosmos.kosmodarts.ui.game.toGameScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The confirmed win of a match waits for the archive (AR1 to AR3, AR9). The game stays on screen, its confirm button
 * busy, until the archive answers; a failure keeps the game and the saved file, and the same button tries again.
 */
class AppReducerArchiveTest {

    private val reducer = AppReducer(Random(0))
    private val keys = MatchEvents(reducer)

    private fun x01Win(vararg players: String): AppState {
        val start = AppState(screen = gameScreen(GameScreenState(MatchFixtures.match(*players))))
        return keys.playLegWonBy(start, winner = 0)
    }

    private fun cricketWin(match: CricketMatch): AppState =
        AppState(screen = cricketGameScreen(CricketGameScreenState(match, isAwaitingConfirmation = true)))

    private fun AppState.archive(): ArchiveStatus = when (val current = screen) {
        is Screen.Game -> current.game.archive
        is Screen.CricketGame -> current.game.archive
        else -> error("no game on $current")
    }

    private fun AppState.archived() = (archive() as ArchiveStatus.Saving).archived

    private fun failedX01Win(): AppState {
        val saving = reducer.reduce(x01Win("Ann", "Bob"), AppEvent.FinishGameTapped)
        return reducer.reduce(saving, AppEvent.ArchiveFailed)
    }

    @Test
    fun `AR1 AR2 confirming the match win asks the archive, and a success opens the end screen`() {
        val won = x01Win("Ann", "Bob")

        val saving = reducer.reduce(won, AppEvent.FinishGameTapped)

        assertTrue(saving.archive() is ArchiveStatus.Saving)
        assertEquals((won.screen as Screen.Game).journal.matchId, saving.archived().matchId)
        assertEquals(TEST_STAMP.at, saving.archived().endedAt)
        assertTrue(reducer.reduce(saving, AppEvent.ArchiveSucceeded).screen is Screen.End)
    }

    @Test
    fun `AR1 AR2 the same holds for a Cricket match`() {
        val won = cricketWin(CricketSaveFixtures.match("Ann", "Bob").playLegWonBy(0))

        val saving = reducer.reduce(won, AppEvent.FinishGameTapped)

        assertTrue(saving.archive() is ArchiveStatus.Saving)
        assertTrue(reducer.reduce(saving, AppEvent.ArchiveSucceeded).screen is Screen.CricketEnd)
    }

    @Test
    fun `AR3 secondConfirmWhileArchivingEmitsNothing`() {
        listOf(
            reducer.reduce(x01Win("Ann", "Bob"), AppEvent.FinishGameTapped),
            reducer.reduce(
                cricketWin(CricketSaveFixtures.match("Ann", "Bob").playLegWonBy(0)),
                AppEvent.FinishGameTapped,
            ),
        ).forEach { saving ->
            val again = reducer.reduce(saving, AppEvent.FinishGameTapped)

            assertEquals(saving, again)
            assertEquals(emptyList<PersistenceEffect>(), persistenceEffects(saving, again))
            listOf(AppEvent.UndoTapped, AppEvent.Back, AppEvent.MissTapped, AppEvent.NextPlayerTapped)
                .forEach { assertEquals(saving, reducer.reduce(saving, it)) }
        }
    }

    @Test
    fun `AR3 a failure keeps the game, and Confirm asks the archive again`() {
        val saving = reducer.reduce(x01Win("Ann", "Bob"), AppEvent.FinishGameTapped)

        val failed = reducer.reduce(saving, AppEvent.ArchiveFailed)
        val retried = reducer.reduce(failed, AppEvent.FinishGameTapped)

        assertEquals(ArchiveStatus.Failed, failed.archive())
        assertTrue(failed.screen is Screen.Game)
        assertEquals(emptyList<PersistenceEffect>(), persistenceEffects(saving, failed))
        assertEquals(saving.archive(), retried.archive())
        assertEquals(
            listOf(PersistenceEffect.ArchiveMatch::class),
            persistenceEffects(failed, retried).map { it::class },
        )
    }

    @Test
    fun `AR3 Undo after a failure removes the winning dart and clears the message`() {
        val failed = failedX01Win()

        val undone = reducer.reduce(failed, AppEvent.UndoTapped)

        assertEquals(ArchiveStatus.Idle, undone.archive())
        assertFalse((undone.screen as Screen.Game).game.isCheckoutPending)
    }

    @Test
    fun `R6 Abandon after a failure still deletes the saved game`() {
        val failed = failedX01Win()

        val dialog = reducer.reduce(failed, AppEvent.Back)
        val abandoned = reducer.reduce(dialog, AppEvent.AbandonConfirmed)

        assertEquals(listOf(PersistenceEffect.DeleteGame), persistenceEffects(dialog, abandoned))
    }

    @Test
    fun `AR3 processDeathWhileArchivingResumesOnZone`() {
        val won = AppState(screen = gameScreen(SaveFixtures.WAITING_CHECKOUT.toGameScreenState()))
        val saving = reducer.reduce(won, AppEvent.FinishGameTapped)

        // Nothing deletes the file before the archive answers: what is saved is still the game with its win waiting.
        val saved = savedGameOf(saving)
        val resumed = (saved as SavedGame).toGameScreenState()

        assertEquals(savedGameOf(won), saved)
        assertEquals(ArchiveStatus.Idle, resumed.archive)
        assertTrue(resumed.isCheckoutPending)
    }

    @Test
    fun `AR1 cutThroatMatchArchives`() {
        val won = cricketWin(CricketSaveFixtures.NON_THROWER_WIN.match)
        assertEquals(CricketVariant.CUT_THROAT, CricketSaveFixtures.NON_THROWER_WIN.match.config.variant)

        val saving = reducer.reduce(won, AppEvent.FinishGameTapped)

        assertEquals(3, saving.archived().players.size)
        assertEquals(
            listOf(PersistenceEffect.ArchiveMatch::class),
            persistenceEffects(won, saving).map { it::class },
        )
    }

    @Test
    fun `AR1 soloMatchArchives`() {
        listOf(
            x01Win("Ann"),
            cricketWin(CricketSaveFixtures.match("Ann").playLegWonBy(0)),
        ).forEach { won ->
            val saving = reducer.reduce(won, AppEvent.FinishGameTapped)

            assertEquals(1, saving.archived().players.size)
            assertEquals(1, saving.archived().legs.size)
        }
    }

    @Test
    fun `AR1 a journal out of step with the match archives nothing and shows the failure`() {
        val won = x01Win("Ann", "Bob")
        val game = (won.screen as Screen.Game).game
        val unreadable = AppState(screen = gameScreen(game, journal = startedJournal()))

        val failed = reducer.reduce(unreadable, AppEvent.FinishGameTapped)

        assertEquals(ArchiveStatus.Failed, failed.archive())
        assertEquals(emptyList<PersistenceEffect>(), persistenceEffects(unreadable, failed))
    }
}
