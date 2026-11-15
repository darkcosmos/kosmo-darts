package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.player.PlayerListRules
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.PlayerSummary
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.StoredGame
import io.github.darkcosmos.kosmodarts.testing.reduce
import io.github.darkcosmos.kosmodarts.ui.players.PlayersState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** PM1, PM2, PM8, PM10: the Players list in the app navigation and its reads; the actions go with the Profile. */
class PlayersReducerTest {
    private val reducer = AppReducer(Random(0))
    private val kevin = summary(1, "Kevin")
    private val lea = summary(2, "Lea")
    private val ana = summary(3, "Ana")

    private fun summary(id: Long, name: String) = PlayerSummary(id, name, PlayerListRules.nameKey(name), 3, 5_000L)

    private fun home(game: StoredGame? = null) = AppState(screen = Screen.Home(resumableGame = game))

    private fun players(state: AppState): PlayersState = (state.screen as Screen.Players).players

    /** The Players screen opened over [home], with the profiles read. */
    private fun opened(from: AppState = home(), vararg list: PlayerSummary = arrayOf(kevin, lea, ana)): AppState {
        val open = reducer.reduce(from, AppEvent.PlayersTapped)
        return reducer.reduce(open, PlayersEvent.Loaded(players(open).loadId, DbResult.Ok(list.toList())))
    }

    @Test
    fun `PM1 the Players button opens Players over the same home, reads once, and back returns to it`() {
        val from = home(SaveFixtures.MID_TURN)
        val open = reducer.reduce(from, AppEvent.PlayersTapped)
        assertEquals(Screen.Players(from.screen as Screen.Home), open.screen)
        assertEquals(listOf(PersistenceEffect.LoadPlayers(1)), persistenceEffects(from, open))

        val back = reducer.reduce(open, AppEvent.Back)
        assertEquals(from.screen, back.screen)
        assertEquals(emptyList<PersistenceEffect>(), persistenceEffects(open, back))
    }

    @Test
    fun `PM1 the button does nothing while the new game dialog is open`() {
        val dialog = AppState(screen = Screen.Home(SaveFixtures.MID_TURN, newGameDialogMode = GameMode.X01))
        assertSame(dialog, reducer.reduce(dialog, AppEvent.PlayersTapped))
    }

    @Test
    fun `PM8 opening and leaving Players neither writes nor deletes the saved game`() {
        val open = reducer.reduce(home(SaveFixtures.MID_TURN), AppEvent.PlayersTapped)
        assertEquals(SaveFixtures.MID_TURN, savedGameOf(open))
        val effects = persistenceEffects(home(SaveFixtures.MID_TURN), open) +
            persistenceEffects(open, reducer.reduce(open, AppEvent.Back))
        assertTrue(effects.none { it is PersistenceEffect.SaveGame || it == PersistenceEffect.DeleteGame })
    }

    @Test
    fun `PM2 the answer to the read shows the profiles, an older answer is ignored`() {
        val state = opened()
        assertEquals(listOf(kevin, lea, ana), players(state).players)
        assertSame(state, reducer.reduce(state, PlayersEvent.Loaded(99, DbResult.Failed)))
    }

    @Test
    fun `PM2 events for other screens do nothing on Players, and Players events do nothing elsewhere`() {
        val state = opened()
        assertSame(state, reducer.reduce(state, AppEvent.StartRequested))
        val elsewhere = home()
        assertSame(elsewhere, reducer.reduce(elsewhere, PlayersEvent.RetryTapped))
    }
}
