package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.testing.startedJournal
import io.github.darkcosmos.kosmodarts.testing.reduce
import io.github.darkcosmos.kosmodarts.testing.savedGame
import io.github.darkcosmos.kosmodarts.testing.gameScreen
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.persistence.CricketSettings
import io.github.darkcosmos.kosmodarts.persistence.LastSetups
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.persistence.SharedRoster
import io.github.darkcosmos.kosmodarts.persistence.toLastSetups
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState
import io.github.darkcosmos.kosmodarts.ui.game.toGameScreenState
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

/** What each event writes or deletes (rules R3, R6, R7, R12; ADR 0002, decision 2). */
class PersistenceEffectsTest {

    private val reducer = AppReducer(Random(0))
    private val config = SaveFixtures.setup()

    private fun effectsOf(before: AppState, event: AppEvent): List<PersistenceEffect> =
        persistenceEffects(before, reducer.reduce(before, event))

    private fun inGame(screen: GameScreenState) =
        AppState(screen = gameScreen(screen), lastSetup = config.toLastSetups())

    private fun saveOf(state: AppState, event: AppEvent): PersistenceEffect =
        PersistenceEffect.SaveGame(checkNotNull(savedGameOf(reducer.reduce(state, event))))

    private val midTurn = inGame(SaveFixtures.MID_TURN.toGameScreenState())
    private val waiting = inGame(SaveFixtures.WAITING_NORMAL.toGameScreenState())
    private val checkout = inGame(SaveFixtures.WAITING_CHECKOUT.toGameScreenState())

    @Test
    fun `R12 a dart, Undo and Next player save the game`() {
        listOf(
            midTurn to AppEvent.NumberTapped(20),
            midTurn to AppEvent.MissTapped,
            midTurn to AppEvent.UndoTapped,
            waiting to AppEvent.NextPlayerTapped,
            waiting to AppEvent.UndoTapped,
        ).forEach { (state, event) ->
            assertEquals(event.toString(), listOf(saveOf(state, event)), effectsOf(state, event))
        }
    }

    @Test
    fun `R12 the winning dart and Undo in the checkout dialog save the game`() {
        val won = SaveFixtures.WAITING_CHECKOUT.game
        val beforeWin = inGame(SaveFixtures.savedAfter(won.config, won.darts.dropLast(1)).toGameScreenState())
        val winningDart = AppEvent.NumberTapped(1)

        assertEquals(listOf(saveOf(beforeWin, winningDart)), effectsOf(beforeWin, winningDart))
        assertEquals(listOf(saveOf(checkout, AppEvent.UndoTapped)), effectsOf(checkout, AppEvent.UndoTapped))
    }

    /** Q5: the modifier and the abandon dialog are not stored, so they write nothing. */
    @Test
    fun `R12 what does not change the saved game writes nothing`() {
        val withAbandonDialog = reducer.reduce(midTurn, AppEvent.Back)
        val home = AppState(screen = Screen.Home(SaveFixtures.MID_TURN), lastSetup = config.toLastSetups())
        listOf(
            midTurn to AppEvent.MultiplierTapped(Multiplier.DOUBLE),
            midTurn to AppEvent.Back,
            withAbandonDialog to AppEvent.AbandonDismissed,
            waiting to AppEvent.NumberTapped(20), // Ignored: the keypad is disabled.
            home to AppEvent.ResumeTapped,
            home to AppEvent.NewGame(GameMode.X01),
            reducer.reduce(home, AppEvent.NewGame(GameMode.X01)) to AppEvent.NewGameDismissed,
        ).forEach { (state, event) ->
            assertEquals(event.toString(), emptyList<PersistenceEffect>(), effectsOf(state, event))
        }
    }

    @Test
    fun `R6 Finish game deletes the saved game`() {
        assertEquals(listOf(PersistenceEffect.DeleteGame), effectsOf(checkout, AppEvent.FinishGameTapped))
    }

    @Test
    fun `R6 Abandon deletes the saved game`() {
        val withAbandonDialog = reducer.reduce(midTurn, AppEvent.Back)

        assertEquals(listOf(PersistenceEffect.DeleteGame), effectsOf(withAbandonDialog, AppEvent.AbandonConfirmed))
    }

    @Test
    fun `R3 Delete in the new-game dialog deletes the saved game`() {
        val dialog = AppState(screen = Screen.Home(SaveFixtures.MID_TURN, newGameDialogMode = GameMode.X01))

        assertEquals(listOf(PersistenceEffect.DeleteGame), effectsOf(dialog, AppEvent.NewGameConfirmed))
    }

    @Test
    fun `R7 Start saves the last setup and the new game`() {
        val setup = AppState(screen = Screen.Setup(SetupState(301, CheckoutRule.SINGLE_OUT, listOf("Ann", "Bob"))))
        val started = X01Config(listOf("Ann", "Bob"), 301, CheckoutRule.SINGLE_OUT)

        assertEquals(
            listOf(
                PersistenceEffect.SaveLastSetup(X01MatchConfig(started).toLastSetups()),
                PersistenceEffect.SaveGame(savedGame(X01Game(started), false, startedJournal())),
            ),
            effectsOf(setup, AppEvent.StartRequested),
        )
    }

    /** R22: an X01 Start writes the roster and the X01 settings, and keeps the Cricket settings of the last setup. */
    @Test
    fun `R22 an X01 Start keeps the Cricket settings of the last setup`() {
        val cricket = CricketSettings(CricketVariant.CUT_THROAT, isBullInPlay = false, MatchFormat(3, 1))
        val previous = LastSetups(SharedRoster(listOf("Cid"), listOf(ColorSlot.SLOT_3)), cricket = cricket)
        val setup = AppState(
            screen = Screen.Setup(SetupState(301, CheckoutRule.SINGLE_OUT, listOf("Ann", "Bob"))),
            lastSetup = previous,
        )
        val started = X01MatchConfig(X01Config(listOf("Ann", "Bob"), 301, CheckoutRule.SINGLE_OUT))

        val effect = effectsOf(setup, AppEvent.StartRequested).first() as PersistenceEffect.SaveLastSetup

        assertEquals(SharedRoster(listOf("Ann", "Bob"), ColorSlot.byPosition(2)), effect.setups.roster)
        assertEquals(started, effect.setups.x01Setup)
        assertEquals(cricket, effect.setups.cricket)
    }

    @Test
    fun `R7 an invalid Start saves nothing`() {
        val setup = AppState(screen = Screen.Setup(SetupState(names = listOf("", ""))))

        assertEquals(emptyList<PersistenceEffect>(), effectsOf(setup, AppEvent.StartRequested))
    }

    @Test
    fun `R7 Rematch saves the rotated setup and the new game`() {
        val three = X01Config(listOf("Ann", "Bob", "Cid"))
        val end = AppState(
            screen = Screen.End(X01Game(three, emptyList())),
            lastSetup = X01MatchConfig(three).toLastSetups(),
        )
        val rotated = X01Config(listOf("Bob", "Cid", "Ann"))
        // PC7: each player keeps his color slot through the rotation.
        val setup = X01MatchConfig(rotated, colorSlots = listOf(ColorSlot.SLOT_2, ColorSlot.SLOT_3, ColorSlot.SLOT_1))

        assertEquals(
            listOf(
                PersistenceEffect.SaveLastSetup(setup.toLastSetups()),
                PersistenceEffect.SaveGame(savedGame(X01Match.start(setup), false, journal = startedJournal())),
            ),
            effectsOf(end, AppEvent.Rematch),
        )
    }

    /** ADR 0002, decision 2: one case per screen, so that a new screen cannot delete the save by mistake. */
    @Test
    fun `R12 which screens carry the saved game`() {
        val saved = SaveFixtures.WAITING_BUST
        assertEquals(saved, savedGameOf(AppState(screen = gameScreen(saved.toGameScreenState()))))
        assertEquals(saved, savedGameOf(AppState(screen = Screen.Home(resumableGame = saved))))
        assertNull(savedGameOf(AppState(screen = Screen.Home())))
        assertNull(savedGameOf(AppState(screen = Screen.Setup(SetupState()))))
        assertNull(savedGameOf(AppState(screen = Screen.End(saved.game))))
    }

    @Test
    fun `R12 the active modifier is not part of the saved game`() {
        val withDouble = midTurn.copy(
            screen = gameScreen(SaveFixtures.MID_TURN.toGameScreenState().toggleMultiplier(Multiplier.DOUBLE)),
        )

        assertEquals(SaveFixtures.MID_TURN, savedGameOf(withDouble))
    }
}
