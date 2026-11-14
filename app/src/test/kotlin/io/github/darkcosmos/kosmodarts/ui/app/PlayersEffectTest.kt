package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.FakePlayersRepository
import io.github.darkcosmos.kosmodarts.history.MergeResult
import io.github.darkcosmos.kosmodarts.history.PlayerSummary
import io.github.darkcosmos.kosmodarts.history.RenameResult
import io.github.darkcosmos.kosmodarts.persistence.LastSetupCodec
import io.github.darkcosmos.kosmodarts.persistence.LastSetups
import io.github.darkcosmos.kosmodarts.persistence.MemoryStore
import io.github.darkcosmos.kosmodarts.persistence.Persistence
import io.github.darkcosmos.kosmodarts.persistence.SharedRoster
import io.github.darkcosmos.kosmodarts.persistence.X01Settings
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileAnswer
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileRequest
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.Executor

/** PM2 to PM6, PM9, PM10, PS7: the Players and Profile effects run on the right executor and always answer. */
class PlayersEffectTest {
    private val queue = ArrayDeque<Runnable>()
    private val reads = ArrayDeque<Runnable>()
    private val kevin = PlayerSummary(1, "Kevin", "kevin", 2, null)
    private val lea = PlayerSummary(2, "Lea", "lea", 0, null)
    private val events = mutableListOf<AppEvent>()
    private val setupStore = MemoryStore()

    private fun persistence(repository: FakePlayersRepository?) = Persistence(
        MemoryStore(),
        setupStore,
        Executor { queue += it },
        players = repository,
        readExecutor = Executor { reads += it },
    )

    private fun run(repository: FakePlayersRepository?, effect: PersistenceEffect) {
        persistence(repository).applyEffects(listOf(effect)) { events += it }
        generateSequence { queue.removeFirstOrNull() }.forEach(Runnable::run)
        generateSequence { reads.removeFirstOrNull() }.forEach(Runnable::run)
    }

    private fun request(repository: FakePlayersRepository?, request: ProfileRequest) =
        run(repository, PersistenceEffect.RunProfileRequest(1L, request))

    @Test
    fun `PM2 the list is read on the read executor and answered with its load id`() {
        val list = listOf(kevin, lea)
        val repository = FakePlayersRepository(players = DbResult.Ok(list))
        persistence(repository).applyEffects(listOf(PersistenceEffect.LoadPlayers(3))) { events += it }

        assertEquals(0, queue.size)
        assertEquals(1, reads.size)
        reads.removeFirst().run()
        assertEquals(listOf<AppEvent>(PlayersEvent.Loaded(3, DbResult.Ok(list))), events)
    }

    @Test
    fun `PS7 the profiles list for a Profile is read on the read executor and answered with both numbers`() {
        val list = listOf(kevin, lea)
        val repository = FakePlayersRepository(players = DbResult.Ok(list))
        persistence(repository).applyEffects(listOf(PersistenceEffect.LoadProfileRoster(1L, 4))) { events += it }

        assertEquals(1, reads.size)
        reads.removeFirst().run()
        assertEquals(listOf<AppEvent>(ProfileEvent.RosterLoaded(1L, 4, DbResult.Ok(list))), events)
    }

    @Test
    fun `PM10 without a repository, or with a failing one, every request answers Failed`() {
        run(null, PersistenceEffect.LoadPlayers(1))
        assertEquals(PlayersEvent.Loaded(1, DbResult.Failed), events.single())

        events.clear()
        val failing = FakePlayersRepository(renameResult = DbResult.Failed)
        request(failing, ProfileRequest.Rename(kevin, "Kev"))
        request(null, ProfileRequest.Reset(kevin, 5L))
        assertEquals(
            listOf<AppEvent>(
                ProfileEvent.Answered(1L, ProfileRequest.Rename(kevin, "Kev"), ProfileAnswer.Renamed(DbResult.Failed)),
                ProfileEvent.Answered(1L, ProfileRequest.Reset(kevin, 5L), ProfileAnswer.ResetDone(DbResult.Failed)),
            ),
            events,
        )
    }

    @Test
    fun `PM3 PM4 PM5 rename, merge and reset run on the save queue with their arguments`() {
        val repository = FakePlayersRepository(
            renameResult = DbResult.Ok(RenameResult.Renamed("Kev", "kev")),
            mergeResult = DbResult.Ok(MergeResult.SHARED_MATCH),
        )
        persistence(repository).applyEffects(
            listOf(
                PersistenceEffect.RunProfileRequest(1L, ProfileRequest.Rename(kevin, "Kev")),
                PersistenceEffect.RunProfileRequest(1L, ProfileRequest.Merge(kevin, lea)),
                PersistenceEffect.RunProfileRequest(2L, ProfileRequest.Reset(lea, 9L)),
            ),
        ) { events += it }
        assertEquals(3, queue.size)
        assertEquals(0, reads.size)
        queue.forEach(Runnable::run)

        assertEquals(listOf(1L to "Kev"), repository.renames)
        assertEquals(listOf(1L to 2L), repository.merges)
        assertEquals(listOf(2L to 9L), repository.resets)
        assertEquals(
            listOf<ProfileAnswer>(
                ProfileAnswer.Renamed(DbResult.Ok(RenameResult.Renamed("Kev", "kev"))),
                ProfileAnswer.Merged(DbResult.Ok(MergeResult.SHARED_MATCH)),
                ProfileAnswer.ResetDone(DbResult.Ok(Unit)),
            ),
            events.map { (it as ProfileEvent.Answered).answer },
        )
    }

    @Test
    fun `PM6 the count is read on the read executor and the delete runs on the save queue`() {
        val repository = FakePlayersRepository(deletionCountResult = DbResult.Ok(4), deleteResult = DbResult.Ok(3))
        persistence(repository).applyEffects(
            listOf(
                PersistenceEffect.RunProfileRequest(1L, ProfileRequest.CountDeletion(kevin)),
                PersistenceEffect.RunProfileRequest(1L, ProfileRequest.Delete(kevin, 8L)),
            ),
        ) { events += it }
        assertEquals(1, reads.size)
        assertEquals(1, queue.size)
        reads.removeFirst().run()
        queue.removeFirst().run()

        assertEquals(listOf(1L to 8L), repository.deletes)
        assertEquals(ProfileAnswer.Counted(DbResult.Ok(4)), (events[0] as ProfileEvent.Answered).answer)
        assertEquals(ProfileAnswer.Deleted(DbResult.Ok(3)), (events[1] as ProfileEvent.Answered).answer)
    }

    @Test
    fun `PM9 a write failure of the last setup is ignored`() {
        setupStore.failOnWrite = true
        val setups = LastSetups(SharedRoster(listOf("Lea"), PlayerColor.byPosition(1)), x01 = X01Settings())
        run(FakePlayersRepository(), PersistenceEffect.SaveLastSetup(setups))
        assertNull(setupStore.content)
        assertEquals(emptyList<AppEvent>(), events)
    }

    @Test
    fun `PM9 the last setup of a deleted only player round-trips and prefills one empty row`() {
        val nameless = LastSetups(SharedRoster(listOf(""), PlayerColor.byPosition(1)), x01 = X01Settings())
        val codec = LastSetupCodec()
        val decoded = codec.decode(codec.encode(nameless))

        assertEquals(nameless, decoded)
        val setup = SetupState.prefilled(decoded, GameMode.CRICKET)
        assertEquals(listOf(""), setup.roster.names)
        assertEquals(PlayerColor.byPosition(1), setup.roster.colors)
        assertEquals(GameMode.CRICKET, setup.mode)
    }
}
