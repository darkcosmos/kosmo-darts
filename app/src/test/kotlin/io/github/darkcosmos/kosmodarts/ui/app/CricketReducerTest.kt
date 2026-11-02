package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.testing.startedJournal
import io.github.darkcosmos.kosmodarts.testing.TEST_STAMP
import io.github.darkcosmos.kosmodarts.testing.reduce
import io.github.darkcosmos.kosmodarts.testing.cricketGameScreen
import io.github.darkcosmos.kosmodarts.testing.cricketBetweenLegs
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatchConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.persistence.playLegWonBy
import io.github.darkcosmos.kosmodarts.persistence.toLastSetups
import io.github.darkcosmos.kosmodarts.ui.cricket.CricketGameScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The Cricket screens of the app reducer (docs/rules/cricket.md, CR22 to CR28; docs/rules/persistence.md, R3, R6,
 * R22).
 */
class CricketReducerTest {

    private val reducer = AppReducer(Random(0))

    private fun AppState.after(vararg events: AppEvent): AppState = events.fold(this, reducer::reduce)

    private val x01Setup = X01MatchConfig(X01Config(listOf("Cid", "Ann"), 301, CheckoutRule.SINGLE_OUT))

    private val duel = CricketSaveFixtures.match("Ann", "Bob", legsPerSet = 2)

    private fun inGame(match: CricketMatch = duel) =
        AppState(screen = cricketGameScreen(CricketGameScreenState(match)))

    /** Ann wins the current leg of [match]; the win waits for "Finish …". */
    private fun withPendingWin(match: CricketMatch) =
        AppState(
            screen = cricketGameScreen(CricketGameScreenState(match.playLegWonBy(0), isAwaitingConfirmation = true)),
        )

    @Test
    fun `R3 New game Cricket without a saved game opens the Cricket setup`() {
        val state = AppState().after(AppEvent.NewGame(GameMode.CRICKET))

        assertEquals(GameMode.CRICKET, (state.screen as Screen.Setup).setup.mode)
    }

    @Test
    fun `R3 R20 New game Cricket with a saved game asks first and keeps the mode`() {
        val home = AppState(screen = Screen.Home(resumableGame = SaveFixtures.WAITING_BUST))
        val dialog = home.after(AppEvent.NewGame(GameMode.CRICKET))

        assertEquals(Screen.Home(SaveFixtures.WAITING_BUST, newGameDialogMode = GameMode.CRICKET), dialog.screen)
        assertTrue((dialog.screen as Screen.Home).isNewGameDialogShown)
        assertEquals(GameMode.CRICKET, (dialog.after(AppEvent.NewGameConfirmed).screen as Screen.Setup).setup.mode)
        assertEquals(home, dialog.after(AppEvent.NewGameDismissed))
    }

    @Test
    fun `R22 a new Cricket match becomes the last setup and keeps the X01 settings`() {
        val setup = CricketMatchConfig(
            CricketConfig(listOf("Ann", "Bob"), CricketVariant.CUT_THROAT, isBullInPlay = false),
            MatchFormat(3, 1),
            ColorSlot.byPosition(2),
        )
        val state = newCricketMatchState(setup, x01Setup.toLastSetups(), TEST_STAMP)

        assertEquals(
            cricketGameScreen(CricketGameScreenState(CricketMatch.start(setup)), startedJournal(), isNewGame = true),
            state.screen,
        )
        assertEquals(setup, state.lastSetup?.cricketSetup)
        assertEquals(301, state.lastSetup?.x01Setup?.config?.startScore)
        assertEquals(CheckoutRule.SINGLE_OUT, state.lastSetup?.x01Setup?.config?.checkoutRule)
    }

    @Test
    fun `CR24 the dart events reach the game`() {
        val state = inGame().after(
            AppEvent.CricketDartTapped(Dart.triple(20)),
            AppEvent.CricketDartTapped(Dart.OUTER_BULL),
            AppEvent.CricketDartTapped(Dart.BULL),
            AppEvent.NextPlayerTapped,
            AppEvent.MissTapped,
            AppEvent.MissTapped,
            AppEvent.UndoTapped,
        )
        val game = (state.screen as Screen.CricketGame).game

        assertEquals(listOf(Dart.triple(20), Dart.OUTER_BULL, Dart.BULL, Dart.MISS), game.game.darts)
    }

    @Test
    fun `CR24 CricketDartTapped records one dart, and undo after a triple removes the triple`() {
        val tapped = inGame().after(AppEvent.CricketDartTapped(Dart.triple(20)), AppEvent.CricketDartTapped(Dart.BULL))
        assertEquals(listOf(Dart.triple(20), Dart.BULL), (tapped.screen as Screen.CricketGame).game.game.darts)

        val undone = inGame().after(AppEvent.CricketDartTapped(Dart.triple(20)), AppEvent.UndoTapped)
        assertEquals(emptyList<Dart>(), (undone.screen as Screen.CricketGame).game.game.darts)
    }

    @Test
    fun `CR1 CR25 CricketDartTapped is ignored for a 25 or Bull with 25-Bull off and while a win is pending`() {
        val noBull = inGame(CricketSaveFixtures.match("Ann", "Bob", isBullInPlay = false))
        val tapped = noBull.after(AppEvent.CricketDartTapped(Dart.OUTER_BULL), AppEvent.CricketDartTapped(Dart.BULL))
        assertEquals(noBull, tapped)

        val win = withPendingWin(duel)
        assertEquals(win, win.after(AppEvent.CricketDartTapped(Dart.single(20))))
    }

    @Test
    fun `M15 Finish does nothing while no win is pending`() {
        val state = inGame()

        assertEquals(state, state.after(AppEvent.FinishGameTapped))
    }

    @Test
    fun `M16 Confirm leg opens the between-legs screen, then Next leg the next leg`() {
        val between = withPendingWin(duel).after(AppEvent.FinishGameTapped)

        assertEquals(cricketBetweenLegs(duel.playLegWonBy(0).confirmLeg()), between.screen)
        val next = between.after(AppEvent.NextLegTapped)
        assertEquals(cricketGameScreen(CricketGameScreenState(duel.confirmLegsWonBy(0))), next.screen)
    }

    @Test
    fun `M17 Confirm match opens the end screen`() {
        val decided = duel.confirmLegsWonBy(0)

        val ended = withPendingWin(decided).after(AppEvent.FinishGameTapped, AppEvent.ArchiveSucceeded)

        assertEquals(Screen.CricketEnd(decided.playLegWonBy(0)), ended.screen)
    }

    @Test
    fun `R6 owner O1 back asks to abandon, even with a pending win, and dismissing keeps the win`() {
        val won = withPendingWin(duel)
        val dialog = won.after(AppEvent.Back)

        assertTrue((dialog.screen as Screen.CricketGame).game.isAbandonDialogShown)
        assertEquals(won, dialog.after(AppEvent.AbandonDismissed))
        assertEquals(Screen.Home(), dialog.after(AppEvent.AbandonConfirmed).screen)
    }

    @Test
    fun `R6 back between legs asks to abandon the match`() {
        val between = AppState(screen = cricketBetweenLegs(duel.confirmLegsWonBy(1)))
        val dialog = between.after(AppEvent.Back)

        assertEquals(cricketBetweenLegs(duel.confirmLegsWonBy(1), isAbandonDialogShown = true), dialog.screen)
        assertEquals(between, dialog.after(AppEvent.Back))
        assertEquals(between, dialog.after(AppEvent.AbandonDismissed))
        assertEquals(dialog, dialog.after(AppEvent.NextLegTapped))
        assertEquals(Screen.Home(), dialog.after(AppEvent.AbandonConfirmed).screen)
    }

    @Test
    fun `M17 back from the end goes home, New game opens the setup of its mode`() {
        val end = AppState(screen = Screen.CricketEnd(duel.confirmLegsWonBy(0).playLegWonBy(0)))

        assertEquals(Screen.Home(), end.after(AppEvent.Back).screen)
        val cricketSetup = end.after(AppEvent.NewGame(GameMode.CRICKET)).screen as Screen.Setup
        assertEquals(GameMode.CRICKET, cricketSetup.setup.mode)
        assertEquals(GameMode.X01, (end.after(AppEvent.NewGame(GameMode.X01)).screen as Screen.Setup).setup.mode)
    }

    @Test
    fun `CR28 PC7 a rematch rotates the players, keeps the slots, settings and format, and becomes the last setup`() {
        val slots = listOf(ColorSlot.SLOT_3, ColorSlot.SLOT_1, ColorSlot.SLOT_2)
        val match = CricketSaveFixtures.match(
            "Ann", "Bob", "Cid",
            variant = CricketVariant.CUT_THROAT, isBullInPlay = false, legsPerSet = 2, sets = 3, colorSlots = slots,
        )
        val end = AppState(screen = Screen.CricketEnd(match), lastSetup = x01Setup.toLastSetups())
        val rotated = CricketMatchConfig(
            CricketConfig(listOf("Bob", "Cid", "Ann"), CricketVariant.CUT_THROAT, isBullInPlay = false),
            MatchFormat(2, 3),
            listOf(ColorSlot.SLOT_1, ColorSlot.SLOT_2, ColorSlot.SLOT_3),
        )

        val rematch = end.after(AppEvent.Rematch)

        assertEquals(rotated, cricketRematchSetup(match))
        assertEquals(
            cricketGameScreen(CricketGameScreenState(CricketMatch.start(rotated)), startedJournal(), isNewGame = true),
            rematch.screen,
        )
        assertEquals(rotated, rematch.lastSetup?.cricketSetup)
        assertEquals(301, rematch.lastSetup?.x01Setup?.config?.startScore)
    }
}
