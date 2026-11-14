package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor
import io.github.darkcosmos.kosmodarts.engine.player.PlayerListError
import io.github.darkcosmos.kosmodarts.engine.player.PlayerListRules
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.MergeResult
import io.github.darkcosmos.kosmodarts.history.PlayerSummary
import io.github.darkcosmos.kosmodarts.history.RenameResult
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.LastSetups
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.SharedRoster
import io.github.darkcosmos.kosmodarts.persistence.StoredGame
import io.github.darkcosmos.kosmodarts.testing.TEST_STAMP
import io.github.darkcosmos.kosmodarts.testing.reduce
import io.github.darkcosmos.kosmodarts.ui.players.PlayersAnswer
import io.github.darkcosmos.kosmodarts.ui.players.PlayersMessage
import io.github.darkcosmos.kosmodarts.ui.players.PlayersPanel
import io.github.darkcosmos.kosmodarts.ui.players.PlayersRequest
import io.github.darkcosmos.kosmodarts.ui.players.PlayersState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** PM1 to PM10: the Players screen in the app's navigation, its reads and requests, and the last setup (PM9). */
class PlayersReducerTest {
    private val reducer = AppReducer(Random(0))
    private val kevin = summary(1, "Kevin")
    private val lea = summary(2, "Lea")
    private val ana = summary(3, "Ana")

    private fun summary(id: Long, name: String) = PlayerSummary(id, name, PlayerListRules.nameKey(name), 3, 5_000L)

    private fun setups(vararg names: String) =
        LastSetups(SharedRoster(names.toList(), PlayerColor.byPosition(names.size)))

    private fun home(game: StoredGame? = null, setup: LastSetups? = null) =
        AppState(screen = Screen.Home(resumableGame = game), lastSetup = setup)

    private fun players(state: AppState): PlayersState = (state.screen as Screen.Players).players

    /** The Players screen opened over [home], with the profiles read. */
    private fun opened(from: AppState = home(), vararg list: PlayerSummary = arrayOf(kevin, lea, ana)): AppState {
        val open = reducer.reduce(from, AppEvent.PlayersTapped)
        return reducer.reduce(open, PlayersEvent.Loaded(players(open).loadId, DbResult.Ok(list.toList())))
    }

    private fun AppState.after(vararg events: AppEvent): AppState = events.fold(this) { s, e -> reducer.reduce(s, e) }

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
        assertSame(elsewhere, reducer.reduce(elsewhere, PlayersEvent.RenameTapped))
    }

    @Test
    fun `PM8 the names of the saved X01 game lock the profiles, whatever their spelling`() {
        val locked = SaveFixtures.NAMES.first().lowercase()
        val state = opened(home(SaveFixtures.MID_TURN), summary(7, locked), lea)
            .after(PlayersEvent.ProfileTapped(7), PlayersEvent.RenameTapped)
        assertEquals(PlayersMessage.InProgress(locked), players(state).message)
        assertNull(players(state).panel)
    }

    @Test
    fun `PM8 the names of a saved Cricket game lock the profiles too`() {
        val name = CricketSaveFixtures.MID_TURN.match.matchConfig.config.players.first()
        val state = opened(home(CricketSaveFixtures.MID_TURN), summary(7, name))
            .after(PlayersEvent.ProfileTapped(7), PlayersEvent.DeleteTapped)
        assertEquals(PlayersMessage.InProgress(name), players(state).message)
        assertNull(players(state).request)
    }

    @Test
    fun `PM8 a profile that is not in the saved game, or no saved game at all, locks nothing`() {
        val free = opened(home(SaveFixtures.MID_TURN)).after(PlayersEvent.ProfileTapped(1), PlayersEvent.RenameTapped)
        assertEquals(PlayersPanel.Rename("Kevin"), players(free).panel)
        val none = opened().after(PlayersEvent.ProfileTapped(1), PlayersEvent.DeleteTapped)
        assertEquals(PlayersRequest.CountDeletion(kevin), players(none).request)
    }

    @Test
    fun `PM8 a reset is allowed for a profile of the saved game`() {
        val name = SaveFixtures.NAMES.first()
        val state = opened(home(SaveFixtures.MID_TURN), summary(7, name))
            .after(PlayersEvent.ProfileTapped(7), PlayersEvent.ResetTapped)
        assertEquals(PlayersPanel.ResetConfirm, players(state).panel)
    }

    @Test
    fun `PM3 a request is run once, when it appears, and its answer reads the list again`() {
        val before = opened().after(PlayersEvent.ProfileTapped(1), PlayersEvent.RenameTapped)
        val saving = reducer.reduce(before, PlayersEvent.RenameSaved)
        val request = PlayersRequest.Rename(kevin, "Kevin")
        assertEquals(listOf(PersistenceEffect.RunPlayersRequest(request)), persistenceEffects(before, saving))
        assertEquals(emptyList<PersistenceEffect>(), persistenceEffects(saving, saving))

        val answer = PlayersAnswer.Renamed(DbResult.Ok(RenameResult.Renamed("Kevin", "kevin")))
        val done = reducer.reduce(saving, PlayersEvent.Answered(request, answer))
        assertEquals(listOf(PersistenceEffect.LoadPlayers(2)), persistenceEffects(saving, done))
    }

    @Test
    fun `PM5 PM6 the confirmations carry the time of the tap`() {
        val reset = opened().after(PlayersEvent.ProfileTapped(1), PlayersEvent.ResetTapped, PlayersEvent.Confirmed)
        assertEquals(PlayersRequest.Reset(kevin, TEST_STAMP.at), players(reset).request)

        val deleting = opened().after(PlayersEvent.ProfileTapped(1), PlayersEvent.DeleteTapped)
            .let { reducer.reduce(it, PlayersEvent.Answered(checkNotNull(players(it).request), counted(2))) }
            .after(PlayersEvent.Confirmed)
        assertEquals(PlayersRequest.Delete(kevin, TEST_STAMP.at), players(deleting).request)
    }

    private fun counted(count: Int) = PlayersAnswer.Counted(DbResult.Ok(count))

    private fun answer(state: AppState, request: PlayersRequest, answer: PlayersAnswer): AppState =
        reducer.reduce(state, PlayersEvent.Answered(request, answer))

    private fun renamed(name: String) = PlayersAnswer.Renamed(DbResult.Ok(RenameResult.Renamed(name, name.lowercase())))

    @Test
    fun `PM9 a rename gives the entry of the last setup the new spelling and keeps the colors`() {
        val state = opened(home(setup = setups("Kevin", "Lea")))
        val done = answer(state, PlayersRequest.Rename(kevin, "KEVIN"), renamed("KEVIN"))
        assertEquals(listOf("KEVIN", "Lea"), done.lastSetup?.roster?.players)
        assertEquals(PlayerColor.byPosition(2), done.lastSetup?.roster?.colors)
        assertEquals(listOf(PersistenceEffect.SaveLastSetup(checkNotNull(done.lastSetup))), setupEffects(state, done))
    }

    private fun setupEffects(before: AppState, after: AppState) =
        persistenceEffects(before, after).filterIsInstance<PersistenceEffect.SaveLastSetup>()

    @Test
    fun `PM9 a merge renames the source entry to the target, or removes it when the target is in the roster`() {
        val request = PlayersRequest.Merge(kevin, ana)
        val merged = PlayersAnswer.Merged(DbResult.Ok(MergeResult.MERGED))

        val absent = answer(opened(home(setup = setups("Kevin", "Lea"))), request, merged)
        assertEquals(listOf("Ana", "Lea"), absent.lastSetup?.roster?.players)
        assertEquals(PlayerColor.byPosition(2), absent.lastSetup?.roster?.colors)

        val present = answer(opened(home(setup = setups("Lea", "Kevin", "Ana"))), request, merged)
        assertEquals(listOf("Lea", "Ana"), present.lastSetup?.roster?.players)
        val colors = PlayerColor.byPosition(3)
        assertEquals(listOf(colors[0], colors[2]), present.lastSetup?.roster?.colors)
    }

    @Test
    fun `PM9 a delete removes the entry, and the only entry stays with an empty name and its color`() {
        val request = PlayersRequest.Delete(kevin, TEST_STAMP.at)
        val deleted = PlayersAnswer.Deleted(DbResult.Ok(1))

        val several = answer(opened(home(setup = setups("Lea", "Kevin"))), request, deleted)
        assertEquals(listOf("Lea"), several.lastSetup?.roster?.players)
        assertEquals(PlayerColor.byPosition(2).take(1), several.lastSetup?.roster?.colors)

        val only = answer(opened(home(setup = setups("Kevin"))), request, deleted)
        assertEquals(listOf(""), only.lastSetup?.roster?.players)
        assertEquals(PlayerColor.byPosition(1), only.lastSetup?.roster?.colors)
    }

    @Test
    fun `PM9 other entries and a profile that is not in the last setup leave it unchanged`() {
        val setup = setups("Lea", "Ana")
        val state = opened(home(setup = setup))
        assertEquals(setup, answer(state, PlayersRequest.Rename(kevin, "K"), renamed("K")).lastSetup)
        assertEquals(
            setup,
            answer(state, PlayersRequest.Delete(kevin, 1L), PlayersAnswer.Deleted(DbResult.Ok(0))).lastSetup,
        )
    }

    @Test
    fun `PM9 a refusal, an error, a reset or no last setup change nothing`() {
        val setup = setups("Kevin", "Lea")
        val state = opened(home(setup = setup))
        val rename = PlayersRequest.Rename(kevin, "Lea")
        val invalid = RenameResult.Invalid(PlayerListError.BLANK_NAME)
        assertEquals(setup, answer(state, rename, PlayersAnswer.Renamed(DbResult.Ok(invalid))).lastSetup)
        assertEquals(setup, answer(state, rename, PlayersAnswer.Renamed(DbResult.Failed)).lastSetup)
        val merge = PlayersRequest.Merge(kevin, lea)
        assertEquals(setup, answer(state, merge, PlayersAnswer.Merged(DbResult.Ok(MergeResult.SHARED_MATCH))).lastSetup)
        assertEquals(setup, answer(state, merge, PlayersAnswer.Merged(DbResult.Failed)).lastSetup)
        val delete = PlayersRequest.Delete(kevin, 1L)
        assertEquals(setup, answer(state, delete, PlayersAnswer.Deleted(DbResult.Failed)).lastSetup)
        val reset = PlayersAnswer.ResetDone(DbResult.Ok(Unit))
        assertEquals(setup, answer(state, PlayersRequest.Reset(kevin, 1L), reset).lastSetup)

        val none = answer(opened(), PlayersRequest.Delete(kevin, 1L), PlayersAnswer.Deleted(DbResult.Ok(1)))
        assertNull(none.lastSetup)
    }

    @Test
    fun `PM9 an answer that comes after the player left the screen still updates the last setup`() {
        val state = opened(home(setup = setups("Kevin", "Lea"))).after(AppEvent.Back)
        val done = answer(state, PlayersRequest.Delete(kevin, 1L), PlayersAnswer.Deleted(DbResult.Ok(1)))
        assertEquals(state.screen, done.screen)
        assertEquals(listOf("Lea"), done.lastSetup?.roster?.players)
    }

    @Test
    fun `PM9 the setup screen prefilled after a delete of the only player has one empty row with its color`() {
        val only = answer(
            opened(home(setup = setups("Kevin"))),
            PlayersRequest.Delete(kevin, 1L),
            PlayersAnswer.Deleted(DbResult.Ok(1)),
        )
        val home = reducer.reduce(only.copy(screen = Screen.Home()), AppEvent.NewGame(GameMode.X01))
        val setup = (home.screen as Screen.Setup).setup
        assertEquals(listOf(""), setup.roster.names)
        assertEquals(PlayerColor.byPosition(1), setup.roster.colors)
    }
}
