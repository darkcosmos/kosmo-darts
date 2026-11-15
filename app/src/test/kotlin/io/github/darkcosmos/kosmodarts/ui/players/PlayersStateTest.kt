package io.github.darkcosmos.kosmodarts.ui.players

import io.github.darkcosmos.kosmodarts.engine.player.PlayerListRules
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.PlayerSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** PM2, PM10, PS7: the state of the Players screen, a list only, with no database and no screen. */
class PlayersStateTest {
    private val kevin = summary(1, "Kevin")

    private fun summary(id: Long, name: String) = PlayerSummary(id, name, PlayerListRules.nameKey(name), 3, 5_000L)

    private fun loaded(vararg players: PlayerSummary): PlayersState =
        PlayersState().loaded(1, DbResult.Ok(players.toList()))

    @Test
    fun `PM2 a read gives the list, an empty list, or a failure that a retry reads again`() {
        assertEquals(PlayersView.LOADING, PlayersState().view)
        assertEquals(PlayersView.LIST, loaded(kevin).view)
        assertEquals(PlayersView.EMPTY, loaded().view)

        val failed = PlayersState().loaded(1, DbResult.Failed)
        assertEquals(PlayersView.FAILED, failed.view)
        val retried = failed.retry()
        assertEquals(PlayersView.LOADING, retried.view)
        assertEquals(2, retried.loadId)
        assertEquals(PlayersView.LIST, retried.loaded(2, DbResult.Ok(listOf(kevin))).view)
    }

    @Test
    fun `PM10 an answer to an older read is ignored, and a retry only exists after a failure`() {
        val state = loaded(kevin)
        assertSame(state, state.loaded(7, DbResult.Failed))
        assertSame(state, state.retry())
    }

    @Test
    fun `PS7 a reload after a Profile action raises the read number and keeps the list shown`() {
        val state = loaded(kevin)
        val reloaded = state.reload()
        assertEquals(2, reloaded.loadId)
        assertEquals(state.content, reloaded.content)
    }
}
