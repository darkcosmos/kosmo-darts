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
import io.github.darkcosmos.kosmodarts.ui.history.MatchDetailContent
import io.github.darkcosmos.kosmodarts.ui.history.MatchDetailState
import io.github.darkcosmos.kosmodarts.ui.history.x01Detail
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileAnswer
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileMessage
import io.github.darkcosmos.kosmodarts.ui.profile.ProfilePanel
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileRequest
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileRoster
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** PM3 to PM11, PS7: the actions of the Profile in the app's navigation, its requests, exits and the last setup. */
class ProfileActionsReducerTest {
    private val reducer = AppReducer(Random(0))
    private val kevin = summary(1, "Kevin")
    private val lea = summary(2, "Lea")
    private val ana = summary(3, "Ana")

    private fun summary(id: Long, name: String) = PlayerSummary(id, name, PlayerListRules.nameKey(name), 3, 5_000L)

    private fun setups(vararg names: String) =
        LastSetups(SharedRoster(names.toList(), PlayerColor.byPosition(names.size)))

    private fun home(game: StoredGame? = null, setup: LastSetups? = null) =
        AppState(screen = Screen.Home(resumableGame = game), lastSetup = setup)

    private fun AppState.after(vararg events: AppEvent): AppState = events.fold(this) { s, e -> reducer.reduce(s, e) }

    private fun profile(state: AppState): ProfileState = (state.screen as Screen.Profile).profile

    private fun players(state: AppState) = (state.screen as Screen.Players).players

    /** The Profile of [id] opened from the Players list over [from], with the profiles list read. */
    private fun opened(
        from: AppState = home(),
        id: Long = 1,
        vararg list: PlayerSummary = arrayOf(kevin, lea, ana),
    ): AppState {
        val players = reducer.reduce(from, AppEvent.PlayersTapped)
        val listed = reducer.reduce(players, PlayersEvent.Loaded(players(players).loadId, DbResult.Ok(list.toList())))
        val open = reducer.reduce(listed, PlayersEvent.ProfileTapped(id))
        return withRoster(open, id, *list)
    }

    private fun withRoster(state: AppState, id: Long, vararg list: PlayerSummary): AppState =
        reducer.reduce(state, ProfileEvent.RosterLoaded(id, profile(state).loadId, DbResult.Ok(list.toList())))

    private fun answer(state: AppState, request: ProfileRequest, answer: ProfileAnswer, id: Long = 1): AppState =
        reducer.reduce(state, ProfileEvent.Answered(id, request, answer))

    private fun renamed(name: String) = ProfileAnswer.Renamed(DbResult.Ok(RenameResult.Renamed(name, name.lowercase())))

    private fun counted(count: Int) = ProfileAnswer.Counted(DbResult.Ok(count))

    @Test
    fun `PM2 PS1 a tap on a Players row opens the Profile over it, and an unknown row does nothing`() {
        val state = opened()
        val profile = state.screen as Screen.Profile
        assertEquals(1L, profile.profileId)
        assertTrue(profile.parent is Screen.Players)
        val reads = listOf(PersistenceEffect.LoadProfile(1, 1), PersistenceEffect.LoadProfileRoster(1, 1))
        assertEquals(reads, effectsOf(state))

        val players = reducer.reduce(home(), AppEvent.PlayersTapped)
        assertSame(players, reducer.reduce(players, PlayersEvent.ProfileTapped(1)))
    }

    private fun effectsOf(state: AppState): List<PersistenceEffect> =
        profileEffects(AppState(screen = (state.screen as Screen.Profile).parent), state)

    @Test
    fun `PS7 the roster answer reaches the Profile and an older one is ignored`() {
        val state = opened()
        assertEquals(ProfileRoster.Loaded(kevin, listOf(lea, ana)), profile(state).roster)
        assertSame(state, reducer.reduce(state, ProfileEvent.RosterLoaded(1, 99, DbResult.Failed)))
        val retried = state.after(ProfileEvent.RetryTapped)
        val failed = reducer.reduce(retried, ProfileEvent.RosterLoaded(1, 1, DbResult.Failed))
        assertSame(failed, reducer.reduce(failed, ProfileEvent.RosterLoaded(1, 1, DbResult.Failed)))
    }

    @Test
    fun `PS7 PM2 events for the Players list do nothing on a Profile`() {
        val state = opened()
        assertSame(state, reducer.reduce(state, PlayersEvent.RetryTapped))
    }

    @Test
    fun `PS7 the manage toggle opens and folds the actions`() {
        val state = opened()
        assertTrue(profile(state.after(ProfileEvent.ManageToggled)).isManageOpen)
        assertEquals(false, profile(state.after(ProfileEvent.ManageToggled, ProfileEvent.ManageToggled)).isManageOpen)
    }

    @Test
    fun `PM8 the names of the saved X01 game lock the profiles, whatever their spelling`() {
        val locked = SaveFixtures.NAMES.first().lowercase()
        val state = opened(home(SaveFixtures.MID_TURN), 7, summary(7, locked), lea)
            .after(ProfileEvent.RenameTapped)
        assertEquals(ProfileMessage.InProgress(locked), profile(state).message)
        assertNull(profile(state).panel)
    }

    @Test
    fun `PM8 the names of a saved Cricket game lock the profiles too`() {
        val name = CricketSaveFixtures.MID_TURN.match.matchConfig.config.players.first()
        val state = opened(home(CricketSaveFixtures.MID_TURN), 7, summary(7, name))
            .after(ProfileEvent.DeleteTapped)
        assertEquals(ProfileMessage.InProgress(name), profile(state).message)
        assertNull(profile(state).request)
    }

    @Test
    fun `PM8 a profile that is not in the saved game, or no saved game at all, locks nothing`() {
        val free = opened(home(SaveFixtures.MID_TURN)).after(ProfileEvent.RenameTapped)
        assertEquals(ProfilePanel.Rename("Kevin"), profile(free).panel)
        val none = opened().after(ProfileEvent.DeleteTapped)
        assertEquals(ProfileRequest.CountDeletion(kevin), profile(none).request)
    }

    @Test
    fun `PM8 a reset is allowed for a profile of the saved game`() {
        val name = SaveFixtures.NAMES.first()
        val state = opened(home(SaveFixtures.MID_TURN), 7, summary(7, name)).after(ProfileEvent.ResetTapped)
        assertEquals(ProfilePanel.ResetConfirm, profile(state).panel)
    }

    @Test
    fun `PM3 a request is run once, when it appears, and its answer reads the profile and the list again`() {
        val before = opened().after(ProfileEvent.RenameTapped)
        val saving = reducer.reduce(before, ProfileEvent.RenameSaved)
        val request = ProfileRequest.Rename(kevin, "Kevin")
        assertEquals(listOf(PersistenceEffect.RunProfileRequest(1, request)), persistenceEffects(before, saving))
        assertEquals(emptyList<PersistenceEffect>(), persistenceEffects(saving, saving))

        val done = answer(saving, request, renamed("Kevin"))
        val effects = persistenceEffects(saving, done)
        assertTrue(PersistenceEffect.LoadProfile(1, 2) in effects)
        assertTrue(PersistenceEffect.LoadProfileRoster(1, 2) in effects)
        assertTrue(PersistenceEffect.LoadPlayers(2) in effects)
        assertNull(profile(done).request)
    }

    @Test
    fun `PM5 PM6 the confirmations carry the time of the tap`() {
        val reset = opened().after(ProfileEvent.ResetTapped, ProfileEvent.Confirmed)
        assertEquals(ProfileRequest.Reset(kevin, TEST_STAMP.at), profile(reset).request)

        val deleting = opened().after(ProfileEvent.DeleteTapped)
            .let { answer(it, checkNotNull(profile(it).request), counted(2)) }
            .after(ProfileEvent.Confirmed)
        assertEquals(ProfileRequest.Delete(kevin, TEST_STAMP.at), profile(deleting).request)
    }

    @Test
    fun `PS7 PM5 a reset reads the profile again, so the figures and the since date change`() {
        val resetting = opened().after(ProfileEvent.ResetTapped, ProfileEvent.Confirmed)
        val reset = ProfileRequest.Reset(kevin, TEST_STAMP.at)
        val done = answer(resetting, reset, ProfileAnswer.ResetDone(DbResult.Ok(Unit)))
        assertEquals(2, profile(done).loadId)
        assertTrue(done.screen is Screen.Profile)
        assertTrue(PersistenceEffect.LoadProfile(1, 2) in persistenceEffects(resetting, done))
    }

    @Test
    fun `PM4 PS7 a merge from the source lands on the Profile of the target, over the same parent, read again`() {
        val merging = opened()
            .after(ProfileEvent.MergeTapped, ProfileEvent.MergeTargetPicked(3), ProfileEvent.Confirmed)
        val request = ProfileRequest.Merge(kevin, ana)
        assertEquals(request, profile(merging).request)

        val done = answer(merging, request, ProfileAnswer.Merged(DbResult.Ok(MergeResult.MERGED)))
        val screen = done.screen as Screen.Profile
        assertEquals(3L, screen.profileId)
        val parent = screen.parent as Screen.Players
        assertEquals(2, parent.players.loadId)
        assertEquals(
            listOf(PersistenceEffect.LoadPlayers(2), PersistenceEffect.LoadProfile(3, 1)),
            persistenceEffects(merging, done).filter {
                it is PersistenceEffect.LoadPlayers || it is PersistenceEffect.LoadProfile
            },
        )
    }

    @Test
    fun `PM4 PS7 a merge into the shown profile stays on it and reads it again`() {
        val state = opened(id = 3)
        val request = ProfileRequest.Merge(kevin, ana)
        val done = answer(state, request, ProfileAnswer.Merged(DbResult.Ok(MergeResult.MERGED)), id = 3)
        assertEquals(3L, (done.screen as Screen.Profile).profileId)
        assertEquals(2, profile(done).loadId)
    }

    @Test
    fun `PM4 a merge that shares a match, or fails, stays on the Profile with a message`() {
        val request = ProfileRequest.Merge(kevin, ana)
        val state = opened().after(ProfileEvent.MergeTapped, ProfileEvent.MergeTargetPicked(3), ProfileEvent.Confirmed)
        val shared = answer(state, request, ProfileAnswer.Merged(DbResult.Ok(MergeResult.SHARED_MATCH)))
        assertEquals(ProfileMessage.SharedMatch, profile(shared).message)
        assertEquals(1L, (shared.screen as Screen.Profile).profileId)
        val failed = answer(state, request, ProfileAnswer.Merged(DbResult.Failed))
        assertEquals(ProfileMessage.DatabaseError, profile(failed).message)
    }

    @Test
    fun `PM3 a renamed profile reloads the screens behind it, so their names follow`() {
        val state = opened().after(
            ProfileEvent.RenameTapped, ProfileEvent.RenameTextChanged("KEVIN"), ProfileEvent.RenameSaved,
        )
        val done = answer(state, ProfileRequest.Rename(kevin, "KEVIN"), renamed("KEVIN"))
        assertEquals(2, ((done.screen as Screen.Profile).parent as Screen.Players).players.loadId)
    }

    @Test
    fun `PM6 PS7 a delete from a Profile opened by Players goes back to Players, read again`() {
        val deleting = opened().after(ProfileEvent.DeleteTapped)
            .let { answer(it, checkNotNull(profile(it).request), counted(2)) }
            .after(ProfileEvent.Confirmed)
        val done = answer(deleting, ProfileRequest.Delete(kevin, TEST_STAMP.at), ProfileAnswer.Deleted(DbResult.Ok(2)))
        assertEquals(2, players(done).loadId)
        assertTrue(PersistenceEffect.LoadPlayers(2) in persistenceEffects(deleting, done))
    }

    private fun detailScreen(vararg names: String): Screen.MatchDetail {
        val history = Screen.History(Screen.Home())
        val loaded = MatchDetailContent.Loaded(x01Detail(names = names.toList()))
        return Screen.MatchDetail(history, MatchDetailState(7, loaded))
    }

    private fun deleted(state: AppState): AppState =
        answer(state, ProfileRequest.Delete(kevin, TEST_STAMP.at), ProfileAnswer.Deleted(DbResult.Ok(1)))

    @Test
    fun `PM6 PS7 a delete from a match detail that stays goes back to the detail, read again`() {
        val detail = detailScreen("Kevin", "Ana")
        val state = withRoster(AppState(screen = detail.open(Screen.Profile(detail, 1))), 1, kevin, ana)
        val done = deleted(state)
        val screen = done.screen as Screen.MatchDetail
        assertEquals(2, screen.detail.loadId)
    }

    @Test
    fun `PM6 PS7 a delete that erases the match of the detail goes back to the History list, read again`() {
        val detail = detailScreen("Kevin")
        val state = withRoster(AppState(screen = detail.open(Screen.Profile(detail, 1))), 1, kevin, ana)
        val done = deleted(state)
        val screen = done.screen as Screen.History
        assertEquals(2, screen.history.loadId)
    }

    @Test
    fun `PM9 a rename gives the entry of the last setup the new spelling and keeps the colors`() {
        val state = opened(home(setup = setups("Kevin", "Lea")))
        val done = answer(state, ProfileRequest.Rename(kevin, "KEVIN"), renamed("KEVIN"))
        assertEquals(listOf("KEVIN", "Lea"), done.lastSetup?.roster?.players)
        assertEquals(PlayerColor.byPosition(2), done.lastSetup?.roster?.colors)
        assertEquals(listOf(PersistenceEffect.SaveLastSetup(checkNotNull(done.lastSetup))), setupEffects(state, done))
    }

    private fun setupEffects(before: AppState, after: AppState) =
        persistenceEffects(before, after).filterIsInstance<PersistenceEffect.SaveLastSetup>()

    @Test
    fun `PM9 a merge renames the source entry to the target, or removes it when the target is in the roster`() {
        val request = ProfileRequest.Merge(kevin, ana)
        val merged = ProfileAnswer.Merged(DbResult.Ok(MergeResult.MERGED))

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
        val request = ProfileRequest.Delete(kevin, TEST_STAMP.at)
        val deleted = ProfileAnswer.Deleted(DbResult.Ok(1))

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
        assertEquals(setup, answer(state, ProfileRequest.Rename(kevin, "K"), renamed("K")).lastSetup)
        assertEquals(
            setup,
            answer(state, ProfileRequest.Delete(kevin, 1L), ProfileAnswer.Deleted(DbResult.Ok(0))).lastSetup,
        )
    }

    @Test
    fun `PM9 a refusal, an error, a reset or no last setup change nothing`() {
        val setup = setups("Kevin", "Lea")
        val state = opened(home(setup = setup))
        val rename = ProfileRequest.Rename(kevin, "Lea")
        val invalid = RenameResult.Invalid(PlayerListError.BLANK_NAME)
        assertEquals(setup, answer(state, rename, ProfileAnswer.Renamed(DbResult.Ok(invalid))).lastSetup)
        assertEquals(setup, answer(state, rename, ProfileAnswer.Renamed(DbResult.Failed)).lastSetup)
        val merge = ProfileRequest.Merge(kevin, lea)
        assertEquals(setup, answer(state, merge, ProfileAnswer.Merged(DbResult.Ok(MergeResult.SHARED_MATCH))).lastSetup)
        assertEquals(setup, answer(state, merge, ProfileAnswer.Merged(DbResult.Failed)).lastSetup)
        val delete = ProfileRequest.Delete(kevin, 1L)
        assertEquals(setup, answer(state, delete, ProfileAnswer.Deleted(DbResult.Failed)).lastSetup)
        val reset = ProfileAnswer.ResetDone(DbResult.Ok(Unit))
        assertEquals(setup, answer(state, ProfileRequest.Reset(kevin, 1L), reset).lastSetup)

        val none = answer(opened(), ProfileRequest.Delete(kevin, 1L), ProfileAnswer.Deleted(DbResult.Ok(1)))
        assertNull(none.lastSetup)
    }

    @Test
    fun `PM9 an answer that comes after the player left the screen still updates the last setup`() {
        val state = opened(home(setup = setups("Kevin", "Lea"))).after(AppEvent.Back, AppEvent.Back)
        val done = answer(state, ProfileRequest.Delete(kevin, 1L), ProfileAnswer.Deleted(DbResult.Ok(1)))
        assertEquals(state.screen, done.screen)
        assertEquals(listOf("Lea"), done.lastSetup?.roster?.players)
    }

    @Test
    fun `PM9 the setup screen prefilled after a delete of the only player has one empty row with its color`() {
        val only = answer(
            opened(home(setup = setups("Kevin"))),
            ProfileRequest.Delete(kevin, 1L),
            ProfileAnswer.Deleted(DbResult.Ok(1)),
        )
        val home = reducer.reduce(only.copy(screen = Screen.Home()), AppEvent.NewGame(GameMode.X01))
        val setup = (home.screen as Screen.Setup).setup
        assertEquals(listOf(""), setup.roster.names)
        assertEquals(PlayerColor.byPosition(1), setup.roster.colors)
    }
}
