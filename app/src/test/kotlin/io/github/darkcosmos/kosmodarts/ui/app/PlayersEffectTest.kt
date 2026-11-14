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
import io.github.darkcosmos.kosmodarts.ui.players.PlayersAnswer
import io.github.darkcosmos.kosmodarts.ui.players.PlayersRequest
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.Executor

/** PM2 to PM6, PM9, PM10: the Players effects run on the right executor and always answer, with the value or Failed. */
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

    private fun request(repository: FakePlayersRepository?, request: PlayersRequest) =
        run(repository, PersistenceEffect.RunPlayersRequest(request))

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
    fun `PM10 without a repository, or with a failing one, every request answers Failed`() {
        run(null, PersistenceEffect.LoadPlayers(1))
        assertEquals(PlayersEvent.Loaded(1, DbResult.Failed), events.single())

        events.clear()
        val failing = FakePlayersRepository(renameResult = DbResult.Failed)
        request(failing, PlayersRequest.Rename(kevin, "Kev"))
        request(null, PlayersRequest.Reset(kevin, 5L))
        assertEquals(
            listOf<AppEvent>(
                PlayersEvent.Answered(PlayersRequest.Rename(kevin, "Kev"), PlayersAnswer.Renamed(DbResult.Failed)),
                PlayersEvent.Answered(PlayersRequest.Reset(kevin, 5L), PlayersAnswer.ResetDone(DbResult.Failed)),
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
                PersistenceEffect.RunPlayersRequest(PlayersRequest.Rename(kevin, "Kev")),
                PersistenceEffect.RunPlayersRequest(PlayersRequest.Merge(kevin, lea)),
                PersistenceEffect.RunPlayersRequest(PlayersRequest.Reset(lea, 9L)),
            ),
        ) { events += it }
        assertEquals(3, queue.size)
        assertEquals(0, reads.size)
        queue.forEach(Runnable::run)

        assertEquals(listOf(1L to "Kev"), repository.renames)
        assertEquals(listOf(1L to 2L), repository.merges)
        assertEquals(listOf(2L to 9L), repository.resets)
        assertEquals(
            listOf<PlayersAnswer>(
                PlayersAnswer.Renamed(DbResult.Ok(RenameResult.Renamed("Kev", "kev"))),
                PlayersAnswer.Merged(DbResult.Ok(MergeResult.SHARED_MATCH)),
                PlayersAnswer.ResetDone(DbResult.Ok(Unit)),
            ),
            events.map { (it as PlayersEvent.Answered).answer },
        )
    }

    @Test
    fun `PM6 the count is read on the read executor and the delete runs on the save queue`() {
        val repository = FakePlayersRepository(deletionCountResult = DbResult.Ok(4), deleteResult = DbResult.Ok(3))
        persistence(repository).applyEffects(
            listOf(
                PersistenceEffect.RunPlayersRequest(PlayersRequest.CountDeletion(kevin)),
                PersistenceEffect.RunPlayersRequest(PlayersRequest.Delete(kevin, 8L)),
            ),
        ) { events += it }
        assertEquals(1, reads.size)
        assertEquals(1, queue.size)
        reads.removeFirst().run()
        queue.removeFirst().run()

        assertEquals(listOf(1L to 8L), repository.deletes)
        assertEquals(PlayersAnswer.Counted(DbResult.Ok(4)), (events[0] as PlayersEvent.Answered).answer)
        assertEquals(PlayersAnswer.Deleted(DbResult.Ok(3)), (events[1] as PlayersEvent.Answered).answer)
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
