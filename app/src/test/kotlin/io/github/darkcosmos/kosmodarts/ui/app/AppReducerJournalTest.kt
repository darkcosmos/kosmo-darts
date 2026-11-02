package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.journal.JournalEvent
import io.github.darkcosmos.kosmodarts.engine.journal.LegReplay
import io.github.darkcosmos.kosmodarts.engine.journal.LegSetup
import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures
import io.github.darkcosmos.kosmodarts.persistence.playLegWonBy
import io.github.darkcosmos.kosmodarts.testing.cricketGameScreen
import io.github.darkcosmos.kosmodarts.testing.gameScreen
import io.github.darkcosmos.kosmodarts.testing.savedGame
import io.github.darkcosmos.kosmodarts.ui.cricket.CricketGameScreenState
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The journal that follows the match on every screen (DJ1, DJ2, DJ4, AR4, AR5), with a clock and ids the test owns. */
class AppReducerJournalTest {

    private val keys = MatchEvents()

    /** Sends events with a clock that ticks by 10 and checks DJ4 after each one. */
    private class Driver(var state: AppState) {
        private val reducer = AppReducer(Random(0))
        private var clock = 0L

        fun send(vararg events: AppEvent): Driver {
            events.forEach { event ->
                clock += TICK
                state = reducer.reduce(state, event, EventStamp(clock, "match-$clock"))
                assertDj4()
            }
            return this
        }

        val journal: MatchJournal
            get() = when (val screen = state.screen) {
                is Screen.Game -> screen.journal
                is Screen.BetweenLegs -> screen.journal
                is Screen.CricketGame -> screen.journal
                is Screen.CricketBetweenLegs -> screen.journal
                else -> error("no journal on $screen")
            }

        val cricketGame: CricketGameScreenState
            get() = (state.screen as Screen.CricketGame).game

        val now: Long
            get() = clock

        /** DJ4: the darts in play of the current leg journal are those of the current leg of the match. */
        private fun assertDj4() {
            when (val screen = state.screen) {
                is Screen.Game -> x01(screen.game.match, screen.journal)
                is Screen.BetweenLegs -> x01(screen.match, screen.journal)
                is Screen.CricketGame -> cricket(screen.game.match, screen.journal)
                is Screen.CricketBetweenLegs -> cricket(screen.match, screen.journal)
                else -> Unit
            }
        }

        private fun x01(match: X01Match, journal: MatchJournal) {
            assertEquals(match.completedLegs.size + 1, journal.legs.size)
            val starter = match.completedLegs.size % match.config.players.size
            val replayed = LegReplay.replay(journal.currentLeg, LegSetup.X01(match.config, starter))
            assertEquals(match.currentLeg.darts, replayed.dartsInPlay)
        }

        private fun cricket(match: CricketMatch, journal: MatchJournal) {
            assertEquals(match.completedLegs.size + 1, journal.legs.size)
            val starter = match.completedLegs.size % match.config.players.size
            val replayed = LegReplay.replay(journal.currentLeg, LegSetup.Cricket(match.config, starter))
            assertEquals(match.currentLeg.darts, replayed.dartsInPlay)
        }

        private companion object {
            const val TICK = 10L
        }
    }

    private fun x01Driver(legsPerSet: Int = 1): Driver =
        Driver(AppState(screen = gameScreen(GameScreenState(match(legsPerSet)))))

    private fun match(legsPerSet: Int) = MatchFixtures.match("Ann", "Bob", legsPerSet = legsPerSet)

    private fun Driver.dart(dart: Dart): Driver {
        send(*keys.eventsOf(dart).toTypedArray())
        return if ((state.screen as Screen.Game).game.canGoToNextPlayer) send(AppEvent.NextPlayerTapped) else this
    }

    private fun Driver.playLegWonBy(winner: Int): Driver {
        val match = (state.screen as Screen.Game).game.match
        MatchFixtures.dartsToWin(match, winner).forEach { dart(it) }
        return this
    }

    private fun Driver.events(): List<JournalEvent> = journal.currentLeg.events

    @Test
    fun `AR4 AR5 Start gives the match its id and its start time and an empty journal`() {
        val setup = Driver(AppState(screen = Screen.Home()))
            .send(AppEvent.NewGame(GameMode.X01), AppEvent.NameChanged(0, "Ann"), AppEvent.NameChanged(1, "Bob"))
            .send(AppEvent.StartRequested)

        assertEquals(MatchJournal("match-40", 40L), setup.journal)
        assertTrue(setup.events().isEmpty())
    }

    @Test
    fun `AR4 AR5 Start of a Cricket match gives it an id, a start time and an empty journal`() {
        val driver = Driver(AppState(screen = Screen.Home()))
            .send(AppEvent.NewGame(GameMode.CRICKET), AppEvent.NameChanged(0, "Ann"), AppEvent.NameChanged(1, "Bob"))
            .send(AppEvent.StartRequested)

        assertEquals(MatchJournal("match-40", 40L), driver.journal)
    }

    @Test
    fun `DJ1 each dart is journaled with the time of its event, in order`() {
        val driver = x01Driver().send(AppEvent.NumberTapped(20)).send(AppEvent.MissTapped)

        assertEquals(
            listOf(10L, 20L),
            driver.events().map { (it as JournalEvent.DartEntered).enteredAt },
        )
        assertEquals(listOf(Dart.single(20), Dart.MISS), driver.events().map { (it as JournalEvent.DartEntered).dart })
    }

    @Test
    fun `DJ2 an undo is journaled, and a no-op undo is not`() {
        val none = x01Driver().send(AppEvent.UndoTapped)
        assertTrue(none.events().isEmpty())

        val driver = x01Driver().send(AppEvent.NumberTapped(20), AppEvent.UndoTapped)
        assertEquals(listOf(JournalEvent.Undone), driver.events().drop(1))
        assertEquals(2, driver.events().size)
    }

    @Test
    fun `DJ2 events that change nothing add nothing to the journal`() {
        val driver = x01Driver().send(AppEvent.NumberTapped(20))
        val before = driver.journal

        driver.send(AppEvent.Back, AppEvent.AbandonDismissed)

        assertEquals(before, driver.journal)
    }

    @Test
    fun `DJ1 a confirmed X01 leg keeps its journal and opens the journal of the next leg`() {
        val driver = x01Driver(legsPerSet = 2).playLegWonBy(0)
        val played = driver.events()

        driver.send(AppEvent.FinishGameTapped)
        assertTrue(driver.state.screen is Screen.BetweenLegs)
        assertEquals(2, driver.journal.legs.size)
        assertEquals(played, driver.journal.legs.first().events)

        driver.send(AppEvent.NextLegTapped)
        assertEquals(2, driver.journal.legs.size)
        assertTrue(driver.events().isEmpty())
    }

    @Test
    fun `DJ1 a confirmed Cricket leg keeps its journal and opens the journal of the next leg`() {
        val match = CricketSaveFixtures.match("Ann", "Bob", legsPerSet = 2)
        val darts = match.playLegWonBy(0).currentLeg.darts
        val driver = Driver(AppState(screen = cricketGameScreen(CricketGameScreenState(match))))

        darts.forEach { dart ->
            driver.send(AppEvent.CricketDartTapped(dart))
            if (driver.cricketGame.canGoToNextPlayer) driver.send(AppEvent.NextPlayerTapped)
        }
        val played = driver.events()
        driver.send(AppEvent.FinishGameTapped)

        assertTrue(driver.state.screen is Screen.CricketBetweenLegs)
        assertEquals(played, driver.journal.legs.first().events)
        driver.send(AppEvent.NextLegTapped)
        assertEquals(2, driver.journal.legs.size)
        assertTrue(driver.events().isEmpty())
    }

    @Test
    fun `DJ2 the undo of the winning dart, with the checkout pending, is journaled`() {
        val driver = x01Driver(legsPerSet = 2).playLegWonBy(0)
        assertTrue((driver.state.screen as Screen.Game).game.isCheckoutPending)

        driver.send(AppEvent.UndoTapped)

        assertEquals(JournalEvent.Undone, driver.events().last())
        assertTrue(!(driver.state.screen as Screen.Game).game.isCheckoutPending)
    }

    @Test
    fun `DJ2 the undo of the winning Cricket dart, with the win pending, is journaled`() {
        val match = CricketSaveFixtures.match("Ann", "Bob", legsPerSet = 2)
        val driver = Driver(AppState(screen = cricketGameScreen(CricketGameScreenState(match))))
        match.playLegWonBy(0).currentLeg.darts.forEach { dart ->
            driver.send(AppEvent.CricketDartTapped(dart))
            if (driver.cricketGame.canGoToNextPlayer) driver.send(AppEvent.NextPlayerTapped)
        }
        assertTrue((driver.state.screen as Screen.CricketGame).game.isWinPending)

        driver.send(AppEvent.UndoTapped)

        assertEquals(JournalEvent.Undone, driver.events().last())
        assertTrue(!(driver.state.screen as Screen.CricketGame).game.isWinPending)
    }

    @Test
    fun `DJ2 an undo after a bust is journaled and DJ4 holds`() {
        val driver = x01Driver()
        // Ann 301: T20 x3 = 180, Bob misses, then T20 T20 brings her to 1 and a third T20 busts.
        repeat(3) { driver.dart(Dart.triple(20)) }
        repeat(3) { driver.dart(Dart.MISS) }
        repeat(3) { driver.dart(Dart.triple(20)) }
        val before = driver.events().size

        driver.send(AppEvent.UndoTapped)

        assertEquals(before + 1, driver.events().size)
        assertEquals(JournalEvent.Undone, driver.events().last())
    }

    @Test
    fun `DJ2 an undo on the first dart after Finish leg journals nothing`() {
        val driver = x01Driver(legsPerSet = 2).playLegWonBy(0).send(AppEvent.FinishGameTapped, AppEvent.NextLegTapped)

        driver.send(AppEvent.UndoTapped)

        assertTrue(driver.events().isEmpty())
        assertEquals(2, driver.journal.legs.size)
    }

    @Test
    fun `AR4 Rematch gives a new id, a new start time and an empty journal, also after an undo`() {
        val driver = x01Driver().playLegWonBy(0).send(AppEvent.UndoTapped)
        val previous = driver.journal
        driver.dart(Dart.MISS)
        driver.send(AppEvent.UndoTapped)
        // Replay the winning dart, then end the match: the only leg is won, so "Finish" ends it.
        val match = (driver.state.screen as Screen.Game).game.match
        MatchFixtures.dartsToWin(match, 0).forEach { driver.dart(it) }
        driver.send(AppEvent.FinishGameTapped, AppEvent.ArchiveSucceeded)
        assertTrue(driver.state.screen is Screen.End)

        driver.send(AppEvent.Rematch)

        assertEquals(MatchJournal("match-${driver.now}", driver.now), driver.journal)
        assertNotEquals(previous.matchId, driver.journal.matchId)
    }

    @Test
    fun `AR4 Resume keeps the id, the start time and the journal of the saved game`() {
        val match = MatchFixtures.match("Ann", "Bob")
        val journal = MatchJournal("saved-id", 42L).enter(Dart.MISS, 43L)
        val saved = savedGame(match.throwDart(Dart.MISS), isAwaitingNextPlayer = false, journal = journal)
        val home = AppState(screen = Screen.Home(resumableGame = saved))

        val driver = Driver(home).send(AppEvent.ResumeTapped)

        assertEquals(journal, driver.journal)
    }
}
