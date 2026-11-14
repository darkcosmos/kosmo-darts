package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.persistence.GameLoad
import io.github.darkcosmos.kosmodarts.persistence.LoadResult
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.settings.inMemoryAudioSettings
import io.github.darkcosmos.kosmodarts.testing.reduce
import io.github.darkcosmos.kosmodarts.ui.history.HistoryScroll
import io.github.darkcosmos.kosmodarts.ui.history.MatchDetailState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** HS6, HS9: the back stack of the history screens, as a parent chain. */
class NavigationStackTest {
    private val reducer = AppReducer(Random(0))
    private val home = Screen.Home(resumableGame = SaveFixtures.MID_TURN)

    private fun back(screen: Screen): Screen = reducer.reduce(AppState(screen = screen), AppEvent.Back).screen

    private fun detail(parent: Screen, id: Long = 5) = Screen.MatchDetail(parent, MatchDetailState(id))

    private fun depth(screen: Screen): Int = if (screen is Screen.Stacked) 1 + depth(screen.parent) else 1

    @Test
    fun `HS6 History, detail, Back returns the same History with its filters and scroll`() {
        val list = Screen.History(home).let {
            it.copy(history = it.history.scrolled(3, 40))
        }
        val opened = reducer.reduce(AppState(screen = list), AppEvent.HistoryMatchTapped(5)).screen
        assertTrue(opened is Screen.MatchDetail)
        val returned = back(opened)
        assertSame(list, returned)
        assertEquals(HistoryScroll(3, 40), (returned as Screen.History).history.scroll)
    }

    @Test
    fun `HS8 a name tapped in a detail opens its Profile over the detail, and Back returns to the detail`() {
        val list = Screen.History(home)
        val opened = detail(list)
        val profile = reducer.reduce(AppState(screen = opened), AppEvent.DetailPlayerTapped(2)).screen
        assertTrue(profile is Screen.Profile)
        assertEquals(2L, (profile as Screen.Profile).profileId)
        assertSame(opened, profile.parent)
        assertSame(opened, back(profile))
    }

    @Test
    fun `HS8 HS9 a name tapped for a Profile already in the stack returns to it, without a copy`() {
        val players = Screen.Players(home)
        val profile = players.open(Screen.Profile(players, 2)) as Screen.Profile
        val opened = profile.open(detail(profile))
        val result = reducer.reduce(AppState(screen = opened), AppEvent.DetailPlayerTapped(2)).screen
        assertSame(profile, result)
    }

    @Test
    fun `HS9 Back from Players or History returns home as before`() {
        assertSame(home, back(Screen.Players(home)))
        assertSame(home, back(Screen.History(home)))
    }

    @Test
    fun `HS9 Back from a Profile or a head-to-head returns one level`() {
        val players = Screen.Players(home)
        val profile = players.open(Screen.Profile(players, 1))
        val h2h = profile.open(Screen.HeadToHead(profile, 1, 2))
        assertSame(profile, back(h2h))
        assertSame(players, back(profile))
    }

    @Test
    fun `HS9 opening a profile already in the stack returns to it and drops the screens above`() {
        val players = Screen.Players(home)
        val profileA = players.open(Screen.Profile(players, 1))
        val matchDetail = profileA.open(detail(profileA))
        val again = matchDetail.open(Screen.Profile(matchDetail, 1))
        assertSame(profileA, again)
        assertEquals(2, depth(again))
        assertSame(players, back(again))
    }

    @Test
    fun `HS9 the same pair in the other order is another screen and each Back returns one level`() {
        val players = Screen.Players(home)
        val profileA = players.open(Screen.Profile(players, 1))
        val h2hAB = profileA.open(Screen.HeadToHead(profileA, 1, 2))
        val detailAB = h2hAB.open(detail(h2hAB))
        val profileB = detailAB.open(Screen.Profile(detailAB, 2))
        val h2hBA = profileB.open(Screen.HeadToHead(profileB, 2, 1))
        assertEquals(6, depth(h2hBA))
        assertSame(profileB, back(h2hBA))
        assertSame(detailAB, back(back(h2hBA)))
        assertSame(h2hAB, back(back(back(h2hBA))))
        assertSame(h2hAB, h2hBA.open(Screen.HeadToHead(h2hBA, 1, 2)))
    }

    @Test
    fun `HS9 the same match is never stacked twice`() {
        val list = Screen.History(home)
        val first = detail(list, 5)
        assertSame(first, first.open(detail(first, 5)))
        assertEquals(3, depth(first.open(detail(first, 6))))
    }

    @Test
    fun `HS9 the stack lives in the ViewModel state, so a rotation leaves it unchanged`() {
        val players = Screen.Players(home)
        val profile = players.open(Screen.Profile(players, 1))
        val stack = profile.open(detail(profile))
        val viewModel = KosmoDartsViewModel(Random(0), AppState(screen = stack), inMemoryAudioSettings())
        // A rotation recreates the activity and composes again over the same ViewModel, whose state is not touched.
        val before = viewModel.state.value
        assertSame(stack, before.screen)
        assertSame(stack, viewModel.state.value.screen)
        assertNull(viewModel.historySnapshot)
    }

    @Test
    fun `HS9 after a process death the app restarts at home, never on a Profile or a head-to-head`() {
        val players = Screen.Players(home)
        val profile = players.open(Screen.Profile(players, 1))
        val stack = profile.open(Screen.HeadToHead(profile, 1, 2))
        val dying = KosmoDartsViewModel(Random(0), AppState(screen = stack), inMemoryAudioSettings())
        // Nothing of these screens is stored: the activity stores no History snapshot for them.
        val snapshot = dying.historySnapshot
        assertNull(snapshot)

        val restarted = KosmoDartsViewModel(
            Random(0),
            initialState(LoadResult(GameLoad.Absent, null), wasOnGameScreen = false, history = snapshot),
            inMemoryAudioSettings(),
        )
        assertEquals(Screen.Home(), restarted.state.value.screen)
    }

    @Test
    fun `HS9 a detail opened from a Profile stores no snapshot and keeps the saved game of the home`() {
        val players = Screen.Players(home)
        val profile = players.open(Screen.Profile(players, 1))
        val stack = profile.open(detail(profile))
        assertEquals(home.resumableGame, savedGameOf(AppState(screen = stack)))
        assertTrue(persistenceEffects(AppState(screen = profile), AppState(screen = stack)).none {
            it is PersistenceEffect.LoadHistory
        })
    }
}
