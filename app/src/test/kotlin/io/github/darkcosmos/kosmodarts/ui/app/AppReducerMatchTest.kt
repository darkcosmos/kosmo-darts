package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.persistence.GameLoad
import io.github.darkcosmos.kosmodarts.persistence.LoadResult
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures.match
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.persistence.playLegWonBy
import io.github.darkcosmos.kosmodarts.settings.AudioSettingsRepository
import io.github.darkcosmos.kosmodarts.settings.InMemoryAudioSettingsStore
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The match flow of the reducer and of the first state (docs/rules/match.md, M13, M15 to M17; R14). */
class AppReducerMatchTest {

    private val events = MatchEvents()

    private fun inGame(match: X01Match) = AppState(screen = Screen.Game(GameScreenState(match)))

    private fun gameOf(state: AppState): GameScreenState = (state.screen as Screen.Game).game

    /** M15, M16: "Finish leg" confirms the leg and opens the between-legs screen with the next leg started. */
    @Test
    fun `M16 Finish leg opens the between-legs screen`() {
        val won = events.playLegWonBy(inGame(match("Ann", "Bob", legsPerSet = 2)), winner = 0)
        assertEquals(LegOutcome.LEG, gameOf(won).checkoutOutcome)

        val between = events.after(won, AppEvent.FinishGameTapped)

        assertEquals(
            Screen.BetweenLegs(gameOf(won).match.confirmLeg(), checkout = gameOf(won).checkoutSummary),
            between.screen,
        )
        assertTrue((between.screen as Screen.BetweenLegs).checkout != null)
    }

    /** M15, M16: "Finish set" does the same. */
    @Test
    fun `M16 Finish set opens the between-legs screen`() {
        val won = events.playLegWonBy(inGame(match("Ann", "Bob", legsPerSet = 1, sets = 2)), winner = 1)
        assertEquals(LegOutcome.SET, gameOf(won).checkoutOutcome)

        val between = events.after(won, AppEvent.FinishGameTapped).screen as Screen.BetweenLegs

        assertEquals(listOf(0, 1), between.match.score.setsWon)
        assertEquals(1, between.match.currentLeg.startingPlayerIndex)
    }

    /** M15, M17: "Finish game" of the deciding leg opens the end screen with the match. */
    @Test
    fun `M17 Finish game ends the match`() {
        val won = events.playLegWonBy(inGame(match("Ann", "Bob", legsPerSet = 2).confirmLegsWonBy(0)), winner = 0)
        assertEquals(LegOutcome.MATCH, gameOf(won).checkoutOutcome)

        assertEquals(Screen.End(gameOf(won).match), events.after(won, AppEvent.FinishGameTapped).screen)
    }

    /** M16: "Next leg" opens the game screen on the new leg, started by the next player (M6). */
    @Test
    fun `M16 Next leg opens the new leg`() {
        val next = match("Ann", "Bob", "Cid", legsPerSet = 2).confirmLegsWonBy(2)
        val state = events.after(AppState(screen = Screen.BetweenLegs(next)), AppEvent.NextLegTapped)

        assertEquals(Screen.Game(GameScreenState(next)), state.screen)
        assertEquals(1, gameOf(state).highlightedPlayerIndex)
    }

    /** M16 (owner Q6 i, Q7): back asks "Abandon the game?"; "Keep playing" or back closes it; "Abandon" goes home. */
    @Test
    fun `M16 back on the between-legs screen asks to abandon`() {
        val between = AppState(screen = Screen.BetweenLegs(match("Ann", "Bob", legsPerSet = 2).confirmLegsWonBy(0)))

        val asking = events.after(between, AppEvent.Back)
        assertEquals((between.screen as Screen.BetweenLegs).copy(isAbandonDialogShown = true), asking.screen)
        assertEquals(between, events.after(asking, AppEvent.AbandonDismissed))
        assertEquals(between, events.after(asking, AppEvent.Back))
        assertEquals(Screen.Home(), events.after(asking, AppEvent.AbandonConfirmed).screen)
        // "Next leg" does nothing while the dialog is open, nor do keypad events on this screen.
        assertEquals(asking, events.after(asking, AppEvent.NextLegTapped))
        assertEquals(between, events.after(between, AppEvent.MissTapped, AppEvent.UndoTapped))
    }

    /** M17 (owner Q3): a rematch keeps the format, rotates the order, and becomes the last setup (R7, R13). */
    @Test
    fun `M17 a rematch keeps the format and rotates the order`() {
        val format = MatchFormat(legsPerSet = 2, sets = 3)
        val config = X01Config(listOf("Ann", "Bob", "Cid"), 301, CheckoutRule.SINGLE_OUT)
        val start = X01Match.start(X01MatchConfig(config, format))
        val ended = AppState(screen = Screen.End(start.playLegWonBy(0)))

        val rematch = events.after(ended, AppEvent.Rematch)

        // PC7: each player keeps his color slot through the rotation.
        val slots = listOf(ColorSlot.SLOT_2, ColorSlot.SLOT_3, ColorSlot.SLOT_1)
        val rotated = X01Config(listOf("Bob", "Cid", "Ann"), 301, CheckoutRule.SINGLE_OUT)
        val expected = X01MatchConfig(rotated, format, slots)
        assertEquals(expected, rematch.lastSetup)
        assertEquals(Screen.Game(GameScreenState(X01Match.start(expected)), isNewGame = true), rematch.screen)
    }

    /** M13, R7: Start builds the match with the setup format and remembers it. */
    @Test
    fun `M13 Start starts a match with the chosen format`() {
        val setup = SetupState(names = listOf("Ann", "Bob"))
        val state = events.after(
            AppState(screen = Screen.Setup(setup)),
            AppEvent.LegsPerSetChanged(1),
            AppEvent.LegsPerSetChanged(1),
            AppEvent.SetsChanged(1),
            AppEvent.StartRequested,
        )

        val expected = X01MatchConfig(X01Config(listOf("Ann", "Bob")), MatchFormat(legsPerSet = 3, sets = 2))
        assertEquals(expected, state.lastSetup)
        assertEquals(expected, gameOf(state).match.matchConfig)
    }

    /** M13, A1: the steppers stop at MatchFormat.MIN and MatchFormat.MAX. */
    @Test
    fun `M13 steppers clamp at the format bounds`() {
        val setup = AppState(screen = Screen.Setup(SetupState()))
        val down = events.after(setup, AppEvent.LegsPerSetChanged(-1), AppEvent.SetsChanged(-1))
        val upEvents = List(MatchFormat.MAX + 2) { listOf(AppEvent.LegsPerSetChanged(1), AppEvent.SetsChanged(1)) }
        val up = events.after(setup, *upEvents.flatten().toTypedArray())

        val downSetup = (down.screen as Screen.Setup).setup
        assertEquals(MatchFormat.MIN to MatchFormat.MIN, downSetup.legsPerSet to downSetup.sets)
        val upSetup = (up.screen as Screen.Setup).setup
        assertEquals(MatchFormat.MAX to MatchFormat.MAX, upSetup.legsPerSet to upSetup.sets)
    }

    /** M13, R7, R13: the next setup is prefilled with the last format. */
    @Test
    fun `R13 the next setup is prefilled with the last format`() {
        val last = X01MatchConfig(X01Config(listOf("Ann", "Bob")), MatchFormat(4, 2))

        val setup = (events.after(AppState(lastSetup = last), AppEvent.NewGame).screen as Screen.Setup).setup

        assertEquals(4 to 2, setup.legsPerSet to setup.sets)
    }

    /** R14 (owner Q6 ii): a kill on the between-legs screen comes back to it; from home, "Resume game" too. */
    @Test
    fun `R14 a saved match between legs resumes on the between-legs screen`() {
        val next = match("Ann", "Bob", legsPerSet = 2).confirmLegsWonBy(0)
        val saved = SavedGame(next, isAwaitingNextPlayer = false, isBetweenLegs = true)

        val afterKill = initialState(LoadResult(GameLoad.Valid(saved), null), wasOnGameScreen = true)
        assertEquals(Screen.BetweenLegs(next), afterKill.screen)

        val home = initialState(LoadResult(GameLoad.Valid(saved), null), wasOnGameScreen = false)
        assertEquals(Screen.Home(resumableGame = saved), home.screen)
        assertEquals(Screen.BetweenLegs(next), events.after(home, AppEvent.ResumeTapped).screen)
    }

    /** R14, M15: a finished, unconfirmed leg comes back in the checkout dialog with its label, for each outcome. */
    @Test
    fun `R14 a resumed checkout dialog keeps its outcome`() {
        val start = match("Ann", "Bob", legsPerSet = 2, sets = 2)
        mapOf(
            start.playLegWonBy(0) to LegOutcome.LEG,
            start.confirmLegsWonBy(0).playLegWonBy(0) to LegOutcome.SET,
            start.confirmLegsWonBy(0, 0, 0).playLegWonBy(0) to LegOutcome.MATCH,
        ).forEach { (finished, outcome) ->
            val saved = SavedGame(finished, isAwaitingNextPlayer = true)

            val state = initialState(LoadResult(GameLoad.Valid(saved), null), wasOnGameScreen = true)

            val game = gameOf(state)
            assertTrue(game.isCheckoutPending)
            assertEquals(outcome, game.checkoutOutcome)
        }
    }

    /** R1, R14: the activity remembers the between-legs screen as a game screen for a return after process death. */
    @Test
    fun `R14 the between-legs screen counts as the game screen for R1`() {
        val between = Screen.BetweenLegs(match("Ann", "Bob", legsPerSet = 2).confirmLegsWonBy(0))
        val onBetween = KosmoDartsViewModel(initial = AppState(screen = between), audioSettings = audioSettings())
        val onHome = KosmoDartsViewModel(initial = AppState(), audioSettings = audioSettings())

        assertTrue(onBetween.isOnGameScreen)
        assertFalse(onHome.isOnGameScreen)
    }

    private fun audioSettings() = AudioSettingsRepository(InMemoryAudioSettingsStore())
}
