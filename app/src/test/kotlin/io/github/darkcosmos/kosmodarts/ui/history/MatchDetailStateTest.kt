package io.github.darkcosmos.kosmodarts.ui.history

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.MatchDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** HS5, HS7, DJ3, CR2, CR14: what the match detail lists, in which order, and how a read is retried. */
class MatchDetailStateTest {
    private val t20 = Dart(20, Multiplier.TRIPLE)

    @Test
    fun `HS5 legs come in order with their set and leg numbers, starter and winner`() {
        val legs = x01Detail(sets = 2, legsPerSet = 3).legs()
        assertEquals(6, legs.size)
        assertEquals(listOf(1 to 1, 1 to 2, 1 to 3, 2 to 1, 2 to 2, 2 to 3), legs.map { it.setNumber to it.legNumber })
        assertTrue(legs.all { it.hasSetNumber })
        assertEquals("Ana", legs[0].starter.name)
        assertEquals("Bob", legs[0].winner.name)
        assertEquals("Bob", legs[1].starter.name)
    }

    @Test
    fun `M19 a match of a single set does not number its sets`() {
        assertFalse(x01Detail(sets = 1, legsPerSet = 3).legs().first().hasSetNumber)
    }

    @Test
    fun `DJ3 a visit lists its darts in rank order, visits in visit order, with X01 totals`() {
        val base = x01Detail(sets = 1, legsPerSet = 1)
        // The darts are stored out of order: rank and visit number decide, not the storage order.
        val detail = base.copy(darts = base.darts.shuffled(kotlin.random.Random(3)))
        val visits = detail.legs().single().visits

        assertEquals(listOf("Ana", "Bob", "Ana"), visits.map { it.player.name })
        assertEquals(listOf(t20, t20, t20), visits[0].darts)
        assertEquals(180, visits[0].total)
        assertEquals(listOf(Dart.BULL, Dart.MISS, Dart(19)), visits[1].darts)
        assertEquals(listOf(Dart(16, Multiplier.DOUBLE)), visits[2].darts)
    }

    @Test
    fun `HS5 a bust visit is flagged, the others are not`() {
        val visits = x01Detail(sets = 1, legsPerSet = 1).legs().single().visits
        assertEquals(listOf(false, true, false), visits.map { it.isBust })
    }

    @Test
    fun `HS5 voided darts are not shown, and a visit of voided darts only is not listed`() {
        val base = x01Detail(sets = 1, legsPerSet = 1)
        val voidedFirst = base.darts.map { if (it.visit == 1 && it.rank == 3) it.copy(isVoided = true) else it }
        val allVoided = voidedFirst.map { if (it.visit == 3) it.copy(isVoided = true) else it }

        val visits = base.copy(darts = allVoided).legs().single().visits

        assertEquals(2, visits.size)
        assertEquals(listOf(t20, t20), visits[0].darts)
        assertEquals(120, visits[0].total)
    }

    @Test
    fun `CR2 CR14 a Cricket visit carries its marks, the Bull counting only when it is in play`() {
        val withBull = cricketDetail(isBullInPlay = true).legs().single().visits.first()
        val withoutBull = cricketDetail(isBullInPlay = false).legs().single().visits.first()

        // T20 (3 marks) + D19 (2 marks) + the Bull (2 marks, or none when the 25 is out of play).
        assertEquals(7, withBull.total)
        assertEquals(5, withoutBull.total)
        assertFalse(withBull.isBust)
    }

    @Test
    fun `HS5 a bust flag stored on a Cricket dart is not shown`() {
        val base = cricketDetail()
        val flagged = base.copy(darts = base.darts.map { it.copy(isBust = true) })
        assertTrue(flagged.legs().single().visits.none { it.isBust })
    }

    @Test
    fun `HS5 the duration is whole minutes, rounded down, never negative`() {
        val detail = x01Detail(minutes = 47)
        assertEquals(47L, detail.durationMinutes())
        assertEquals(0L, detail.copy(endedAt = detail.startedAt + 59_999L).durationMinutes())
        assertEquals(1L, detail.copy(endedAt = detail.startedAt + 60_000L).durationMinutes())
        assertEquals(0L, detail.copy(endedAt = detail.startedAt - 5_000L).durationMinutes())
    }

    @Test
    fun `HS5 the ranking is by final rank`() {
        val detail = x01Detail()
        val reversed = detail.copy(players = detail.players.reversed())
        assertEquals(listOf("Ana", "Bob"), reversed.ranking().map { it.name })
    }

    @Test
    fun `HS5 a single game has no score to show`() {
        assertFalse(x01Detail(sets = 1, legsPerSet = 1).hasScore)
        assertTrue(x01Detail().hasScore)
    }

    @Test
    fun `HS7 the answer to the current read shows the match, a failure shows Failed`() {
        val detail = x01Detail()
        val loaded = MatchDetailState(7).loaded(1, DbResult.Ok(detail))
        assertEquals(MatchDetailContent.Loaded(detail), loaded.content)
        assertEquals(MatchDetailContent.Failed, MatchDetailState(7).loaded(1, DbResult.Failed).content)
    }

    @Test
    fun `HS7 an answer to an older read is ignored`() {
        val retried = MatchDetailState(7).loaded(1, DbResult.Failed).retry()
        assertEquals(2, retried.loadId)
        assertSame(retried, retried.loaded(1, DbResult.Ok(x01Detail())))
    }

    @Test
    fun `HS7 retry reads again after a failure and does nothing otherwise`() {
        val failed = MatchDetailState(7).loaded(1, DbResult.Failed)
        val retried = failed.retry()
        assertEquals(MatchDetailContent.Loading, retried.content)
        assertEquals(2, retried.loadId)

        val loading = MatchDetailState(7)
        assertSame(loading, loading.retry())
        val loaded = loading.loaded(1, DbResult.Ok(x01Detail()))
        assertSame(loaded, loaded.retry())
    }

    @Test(expected = IllegalStateException::class)
    fun `a dart of a player that does not exist is refused, never shown`() {
        val detail: MatchDetail = x01Detail(names = listOf("Ana", "Bob"))
        detail.copy(players = detail.players.take(1)).legs()
    }
}
