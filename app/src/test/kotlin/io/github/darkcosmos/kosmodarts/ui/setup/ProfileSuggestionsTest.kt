package io.github.darkcosmos.kosmodarts.ui.setup

import io.github.darkcosmos.kosmodarts.engine.player.PlayerListRules
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.Profile
import io.github.darkcosmos.kosmodarts.history.ProfileRepository
import io.github.darkcosmos.kosmodarts.testing.reduce
import io.github.darkcosmos.kosmodarts.persistence.MemoryStore
import io.github.darkcosmos.kosmodarts.persistence.Persistence
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.AppReducer
import io.github.darkcosmos.kosmodarts.ui.app.AppState
import io.github.darkcosmos.kosmodarts.ui.app.GameMode
import io.github.darkcosmos.kosmodarts.ui.app.PersistenceEffect
import io.github.darkcosmos.kosmodarts.ui.app.Screen
import io.github.darkcosmos.kosmodarts.ui.app.applyEffects
import io.github.darkcosmos.kosmodarts.ui.app.persistenceEffects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** PF3, D18: the profiles suggested under the focused name field. */
class ProfileSuggestionsTest {

    private fun profile(name: String, at: Long) = Profile(name, PlayerListRules.nameKey(name), at)

    private val kevin = profile("Kevin", 50)
    private val zoe = profile("Zoé", 40)
    private val anna = profile("Anna", 30)

    private fun names(profiles: List<Profile>) = profiles.map { it.name }

    @Test
    fun `PF3 most recently used first`() {
        val all = listOf(anna, kevin, zoe)
        assertEquals(listOf("Kevin", "Zoé", "Anna"), names(suggestions(all, listOf(""), focused = 0)))
    }

    @Test
    fun `PF3 at most 5`() {
        val many = (1..8).map { profile("Joueur$it", it.toLong()) }
        val result = suggestions(many, listOf(""), focused = 0)
        assertEquals(5, result.size)
        assertEquals("Joueur8", result.first().name)
    }

    @Test
    fun `PF3 contains match on name keys, ignoring case`() {
        val all = listOf(kevin, zoe, anna)
        assertEquals(listOf("Anna"), names(suggestions(all, listOf("NN"), focused = 0)))
    }

    @Test
    fun `PF3 no suggestion when nothing matches or nothing is focused`() {
        val all = listOf(kevin, zoe)
        assertTrue(suggestions(all, listOf("xyz"), focused = 0).isEmpty())
        assertTrue(suggestions(all, listOf(""), focused = null).isEmpty())
        assertTrue(suggestions(all, listOf(""), focused = 3).isEmpty())
    }

    @Test
    fun `PF3 names held by other fields are excluded`() {
        val all = listOf(kevin, zoe, anna)
        val result = suggestions(all, listOf("", "kevin ", "Anna"), focused = 0)
        assertEquals(listOf("Zoé"), names(result))
    }

    @Test
    fun `PF3 accents are kept and a decomposed accent matches the composed profile`() {
        val all = listOf(zoe, kevin)
        val decomposed = "Zoé"
        assertEquals(listOf("Zoé"), names(suggestions(all, listOf(decomposed), focused = 0)))
        assertEquals(listOf("Zoé"), names(suggestions(all, listOf("zo"), focused = 0)))
        assertTrue(suggestions(all, listOf("Zoe"), focused = 0).isEmpty())
    }

    @Test
    fun `S4 percent and underscore are plain characters`() {
        val all = listOf(kevin, profile("100%", 20), profile("a_b", 10))
        assertEquals(listOf("100%"), names(suggestions(all, listOf("%"), focused = 0)))
        assertEquals(listOf("a_b"), names(suggestions(all, listOf("_"), focused = 0)))
        assertTrue(suggestions(all, listOf("a%b"), focused = 0).isEmpty())
        assertTrue(suggestions(all, listOf("k_vin"), focused = 0).isEmpty())
    }

    @Test
    fun `D18 the profile whose name is exactly the typed text is hidden, another spelling stays`() {
        val all = listOf(kevin, anna)
        assertTrue(suggestions(all, listOf("Anna"), focused = 0).isEmpty())
        assertTrue(suggestions(all, listOf("Kevin"), focused = 0).isEmpty())
        assertEquals(listOf("Kevin"), names(suggestions(all, listOf("kevin"), focused = 0)))
    }

    @Test
    fun `PF3 setup suggests only under the focused field`() {
        val setup = SetupState(names = listOf("", "")).withProfiles(listOf(kevin, zoe)).withFocusOn(1)
        assertTrue(setup.suggestionsFor(0).isEmpty())
        assertEquals(listOf("Kevin", "Zoé"), names(setup.suggestionsFor(1)))
        assertTrue(setup.withFocusLostBy(1).suggestionsFor(1).isEmpty())
        assertEquals(setup, setup.withFocusLostBy(0))
    }

    @Test
    fun `PF3 tapping a suggestion fills only that field and keeps the rest of the prefill`() {
        val prefilled = SetupState(startScore = 301, names = listOf("Ann", "Bo"), legsPerSet = 3)
            .withProfiles(listOf(kevin))
            .withFocusOn(1)
        val after = AppReducer(Random(0)).reduce(
            AppState(screen = Screen.Setup(prefilled)),
            AppEvent.SuggestionPicked(1, "Kevin"),
        ).screen as Screen.Setup
        assertEquals(listOf("Ann", "Kevin"), after.setup.names)
        assertEquals(301, after.setup.startScore)
        assertEquals(3, after.setup.legsPerSet)
        assertEquals(1, after.setup.focusedIndex)
    }

    @Test
    fun `PF3 opening the setup reads the profiles once, other changes do not`() {
        val reducer = AppReducer(Random(0))
        val home = AppState()
        val setup = reducer.reduce(home, AppEvent.NewGame(GameMode.X01))
        assertTrue(PersistenceEffect.LoadProfiles in persistenceEffects(home, setup))
        val typed = reducer.reduce(setup, AppEvent.NameChanged(0, "A"))
        assertTrue(PersistenceEffect.LoadProfiles !in persistenceEffects(setup, typed))
    }

    @Test
    fun `PF3 loaded profiles reach the open setup and are ignored elsewhere`() {
        val reducer = AppReducer(Random(0))
        val open = AppState(screen = Screen.Setup(SetupState()))
        val loaded = reducer.reduce(open, AppEvent.ProfilesLoaded(listOf(kevin)))
        assertEquals(listOf(kevin), (loaded.screen as Screen.Setup).setup.profiles)
        val home = AppState()
        assertEquals(home, reducer.reduce(home, AppEvent.ProfilesLoaded(listOf(kevin))))
    }

    @Test
    fun `AR9 a failed read gives an empty list, never an exception`() {
        val failing = object : ProfileRepository {
            override fun touchOrCreate(names: List<String>, at: Long): DbResult<Unit> = DbResult.Failed

            override fun recent(): DbResult<List<Profile>> = DbResult.Failed
        }
        val events = mutableListOf<AppEvent>()
        Persistence(MemoryStore(), MemoryStore(), Runnable::run, profiles = failing)
            .applyEffects(listOf(PersistenceEffect.LoadProfiles), events::add)
        assertEquals(listOf<AppEvent>(AppEvent.ProfilesLoaded(emptyList())), events)
        // Start works as before, with no suggestion.
        assertTrue(SetupState(names = listOf("Ann", "Bob")).start() is SetupResult.Valid)
    }

    @Test
    fun `PF3 the read profiles come back in the order of the repository`() {
        val repository = object : ProfileRepository {
            override fun touchOrCreate(names: List<String>, at: Long): DbResult<Unit> = DbResult.Ok(Unit)

            override fun recent(): DbResult<List<Profile>> = DbResult.Ok(listOf(kevin, zoe))
        }
        val events = mutableListOf<AppEvent>()
        Persistence(MemoryStore(), MemoryStore(), Runnable::run, profiles = repository)
            .applyEffects(listOf(PersistenceEffect.LoadProfiles), events::add)
        assertEquals(listOf<AppEvent>(AppEvent.ProfilesLoaded(listOf(kevin, zoe))), events)
    }
}
