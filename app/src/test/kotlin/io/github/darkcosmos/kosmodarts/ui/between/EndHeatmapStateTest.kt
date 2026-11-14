package io.github.darkcosmos.kosmodarts.ui.between

import io.github.darkcosmos.kosmodarts.engine.board.BoardZone
import io.github.darkcosmos.kosmodarts.engine.journal.JournalEvent
import io.github.darkcosmos.kosmodarts.engine.journal.LegJournal
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.persistence.playLegWonBy
import io.github.darkcosmos.kosmodarts.testing.MatchJournals
import io.github.darkcosmos.kosmodarts.testing.betweenLegs
import io.github.darkcosmos.kosmodarts.testing.cricketBetweenLegs
import io.github.darkcosmos.kosmodarts.testing.endScreen
import io.github.darkcosmos.kosmodarts.ui.app.EndHeatmapScreen
import io.github.darkcosmos.kosmodarts.ui.app.EndTab
import io.github.darkcosmos.kosmodarts.ui.app.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Leg heatmap" tab of the between-legs and end screens (HM9, HM10, HM6; DJ2, DJ4; D6): the last leg played, for
 * the selected player, derived from the match journal.
 */
class EndHeatmapStateTest {

    private val names = listOf("Ann", "Bob", "Cid", "Dan")

    private fun x01(playerCount: Int, legsPerSet: Int = 2) =
        MatchFixtures.match(*names.take(playerCount).toTypedArray(), legsPerSet = legsPerSet)

    private fun cricket(playerCount: Int, legsPerSet: Int = 2) =
        CricketSaveFixtures.match(*names.take(playerCount).toTypedArray(), legsPerSet = legsPerSet)

    private fun EndHeatmapScreen.forPlayer(player: Int): EndHeatmapScreen =
        withHeatmap(endTab, player, heatmapZone) as EndHeatmapScreen

    private fun EndHeatmapScreen.totals(): List<Int> =
        names.indices.take(playerNames.size).map { forPlayer(it).heatmapState().section.heatmap.total }

    /** HM9, D6: between legs the empty next leg is not read; the last completed leg is, in X01, 2 to 4 players. */
    @Test
    fun `HM9 between legs X01 reads the last completed leg while the next leg is empty`() {
        for (count in 2..4) {
            val screen = betweenLegs(x01(count).confirmLegsWonBy(count - 1))
            assertTrue(screen.journal.currentLeg.events.isEmpty())

            val winner = screen.forPlayer(count - 1).heatmapState().section
            assertTrue(winner.heatmap.total > 0)
            assertEquals(0, winner.heatmap.misses)
            assertEquals(screen.journal.legs[screen.journal.legs.size - 2].events.size, screen.totals().sum())
            // The others only missed in that leg.
            (0 until count - 1).forEach { other ->
                val heatmap = screen.forPlayer(other).heatmapState().section.heatmap
                assertTrue(heatmap.total > 0)
                assertEquals(heatmap.total, heatmap.misses)
            }
        }
    }

    /** HM9, D6: the same for Cricket, whose darts are flagged Cricket (HM4). */
    @Test
    fun `HM9 between legs Cricket reads the last completed leg while the next leg is empty`() {
        for (count in 2..4) {
            val screen = cricketBetweenLegs(cricket(count).confirmLegsWonBy(0))
            assertTrue(screen.journal.currentLeg.events.isEmpty())

            val winner = screen.forPlayer(0).heatmapState().section
            assertTrue(winner.heatmap.total > 0)
            assertTrue(winner.heatmap.includesCricket)
            assertEquals(screen.journal.legs[screen.journal.legs.size - 2].events.size, screen.totals().sum())
        }
    }

    /** HM9: only the last completed leg counts, not the earlier ones. */
    @Test
    fun `HM9 between legs only the last completed leg counts`() {
        val screen = betweenLegs(x01(2, legsPerSet = 3).confirmLegsWonBy(0, 1))

        val firstLegWinner = screen.forPlayer(0).heatmapState().section.heatmap
        assertEquals(firstLegWinner.total, firstLegWinner.misses) // Ann only missed in leg 2, which Bob won
        assertEquals(1, screen.heatmapPlayer)
        assertEquals(0, screen.forPlayer(1).heatmapState().section.heatmap.misses)
    }

    /** HM9, D6: at the end the current, finished leg is read; 1 to 4 players, X01 and Cricket. */
    @Test
    fun `HM9 end screen reads the finished current leg`() {
        for (count in 1..4) {
            val x01Match = x01(count, legsPerSet = 1).playLegWonBy(count - 1)
            val x01End = endScreen(x01Match)
            assertEquals(x01Match.currentLeg.darts.size, x01End.totals().sum())
            assertEquals(count - 1, x01End.heatmapPlayer)

            val cricketMatch = cricket(count, legsPerSet = 1).playLegWonBy(0)
            val cricketEnd = Screen.CricketEnd(cricketMatch, MatchJournals.of(cricketMatch))
            assertEquals(cricketMatch.currentLeg.darts.size, cricketEnd.totals().sum())
            assertEquals(0, cricketEnd.heatmapPlayer)
        }
    }

    /** DJ2: a dart that was undone is not a dart of the leg. */
    @Test
    fun `DJ2 HM9 an undone dart is excluded`() {
        val match = x01(2).confirmLegsWonBy(0)
        val plain = MatchJournals.of(match)
        val firstLeg = plain.legs.first()
        val withUndo = LegJournal(
            listOf(JournalEvent.DartEntered(Dart.single(1), 0L), JournalEvent.Undone) + firstLeg.events,
        )
        val journal = plain.copy(legs = listOf(withUndo) + plain.legs.drop(1))

        val withUndoneDart = betweenLegs(match, journal).heatmapState().section.heatmap
        val without = betweenLegs(match, plain).heatmapState().section.heatmap

        assertEquals(without, withUndoneDart)
        assertNull(withUndoneDart.counts[BoardZone.of(Dart.single(1))])
    }

    /** HM6: the top 5 lists the most hit zones, ties in board order, with their percentage of all darts. */
    @Test
    fun `HM6 top 5 follows the board order on ties with percentages`() {
        val screen = cricketBetweenLegs(cricket(2).confirmLegsWonBy(0)) // the winner hits eight zones once each
        val section = screen.heatmapState().section

        val hit = section.heatmap.counts.keys
        assertEquals(8, hit.size)
        assertEquals(BoardZone.ALL.filter { it in hit }.take(5), section.topZones.map { it.zone })
        assertTrue(section.topZones.all { it.count == 1 && it.share == 0.125 })
    }

    /** HM6: most hit first on X01 legs, counts never increasing down the list. */
    @Test
    fun `HM6 top 5 of an X01 leg is sorted by darts`() {
        val section = betweenLegs(x01(2).confirmLegsWonBy(0)).heatmapState().section
        val top = section.topZones

        assertTrue(top.size in 1..5)
        assertTrue(top.zipWithNext().all { (a, b) -> a.count >= b.count })
        top.forEach { assertEquals(it.count.toDouble() / section.heatmap.total, it.share, 1e-9) }
    }

    /** HM9: one chip per player with name and colour, the selected one flagged; none in a solo game. */
    @Test
    fun `HM9 chips list the players and none in a solo game`() {
        val match = x01(3).confirmLegsWonBy(1)
        val chips = betweenLegs(match).heatmapState().players

        assertEquals(names.take(3), chips.map { it.name })
        assertEquals(match.colors, chips.map { it.color })
        assertEquals(listOf(false, true, false), chips.map { it.isSelected })

        val solo = endScreen(x01(1, legsPerSet = 1).playLegWonBy(0))
        assertTrue(solo.heatmapState().players.isEmpty())
    }

    /** HM6, T21: the zone of the band follows the player switch, with the values of that player. */
    @Test
    fun `HM6 a zone tap and a player switch keep the zone with the values of that player`() {
        val screen = cricketBetweenLegs(cricket(2).confirmLegsWonBy(0))
        val zone = screen.heatmapState().section.topZones.first().zone

        val tapped = screen.withHeatmap(EndTab.LEG_HEATMAP, 0, zone) as EndHeatmapScreen
        assertEquals(1, tapped.heatmapState().section.selected?.count)

        val other = tapped.forPlayer(1).heatmapState().section
        assertEquals(zone, other.selected?.zone)
        assertEquals(0, other.selected?.count)
        assertEquals(EndTab.LEG_HEATMAP, tapped.heatmapState().tab)
    }

    /** HM7: no dart, no band count and the explicit empty state. */
    @Test
    fun `HM7 a journal without the leg gives the empty heatmap`() {
        val match = x01(2).confirmLegsWonBy(0)
        val screen = betweenLegs(match, MatchJournals.of(match).copy(legs = listOf(LegJournal())))

        assertTrue(screen.heatmapState().section.isEmpty)
        assertFalse(screen.heatmapState().players.isEmpty())
    }
}
