package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.board.BoardZone
import io.github.darkcosmos.kosmodarts.engine.board.BoardZone.Ring
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.journal.LegSetup
import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Tests of the heatmap aggregation (HM1, HM2, HM3, HM4, HM7, HM9, ST1, ST2, ST10); rule ids of docs/rules. */
class HeatmapAggregationTest {

    private val t20 = BoardZone(20, Ring.TRIPLE)
    private val t19 = BoardZone(19, Ring.TRIPLE)
    private val d2 = BoardZone(2, Ring.DOUBLE)

    // The StatsMatchFixtures X01 301 leg: player 0 throws T20 x3, then T20, T19, D2 (profile 1); player 1 misses.
    private val x01Match get() = StatsMatchFixtures.x01(players = 2)
    private val cricketMatch get() = StatsMatchFixtures.cricket(players = 2)

    // ---- profile selection ----

    @Test
    fun `HM1 HM2 counts per zone, misses in the total, percentages sum to 100`() {
        val heatmap = heatmapOf(listOf(x01Match), 2L, StatsFilter.AllModes) // player 1: 3 misses
        assertEquals(3, heatmap.total)
        assertEquals(3, heatmap.misses)
        assertEquals(100.0, heatmap.missShare)

        val mixed = heatmapOf(listOf(x01Match), 1L, StatsFilter.AllModes)
        assertEquals(6, mixed.total)
        assertEquals(mapOf(t20 to 4, t19 to 1, d2 to 1), mixed.counts)
        assertEquals(0, mixed.misses)
        val sum = BoardZone.ALL.sumOf { mixed.share(it) ?: 0.0 } + (mixed.missShare ?: 0.0)
        assertEquals(100.0, sum, 1e-9)
    }

    @Test
    fun `ST1 HM2 voided darts are excluded`() {
        val raw = StatsMatchFixtures.x01Raw(players = 2)
        val voided = raw.darts.first().copy(segment = 5, multiplier = Multiplier.SINGLE, isVoided = true)
        val withVoided = raw.copy(darts = listOf(voided) + raw.darts)

        val heatmap = heatmapOf(listOf(StatsMatchFixtures.valid(withVoided)), 1L, StatsFilter.AllModes)

        assertEquals(6, heatmap.total)
        assertEquals(0, heatmap.counts[BoardZone(5, Ring.SINGLE)] ?: 0)
    }

    @Test
    fun `HM3 HM4 all modes adds X01 and Cricket darts and flags Cricket`() {
        val matches = listOf(x01Match, cricketMatch)

        val all = heatmapOf(matches, 1L, StatsFilter.AllModes)
        assertEquals(14, all.total)
        assertTrue(all.includesCricket)
        assertEquals(5, all.counts[t20]) // 4 in X01 and 1 in Cricket
        assertEquals(1, all.counts[BoardZone(Dart.BULL_SEGMENT, Ring.BULL)])
        assertEquals(1, all.counts[BoardZone(Dart.OUTER_BULL_SEGMENT, Ring.OUTER_BULL)])
    }

    @Test
    fun `HM3 the X01 filter counts only X01 darts and does not flag Cricket`() {
        val heatmap = heatmapOf(listOf(x01Match, cricketMatch), 1L, X01Filter())
        assertEquals(6, heatmap.total)
        assertFalse(heatmap.includesCricket)
    }

    @Test
    fun `HM3 HM4 the Cricket filter counts only Cricket darts and flags Cricket`() {
        val heatmap = heatmapOf(listOf(x01Match, cricketMatch), 1L, CricketFilter())
        assertEquals(8, heatmap.total)
        assertTrue(heatmap.includesCricket)
        assertEquals(1, heatmap.counts[t20])
    }

    @Test
    fun `HM7 an empty selection gives total 0 and null shares`() {
        val heatmap = heatmapOf(emptyList(), 1L, StatsFilter.AllModes)
        assertEquals(0, heatmap.total)
        assertNull(heatmap.share(t20))
        assertNull(heatmap.missShare)
        assertTrue(heatmap.topZones().isEmpty())
        assertFalse(heatmap.includesCricket)
        assertEquals(0, heatmapOf(listOf(x01Match), 99L, StatsFilter.AllModes).total)
    }

    @Test
    fun `HM6 topZones orders by count then by zone order and gives percentages`() {
        val heatmap = Heatmap(
            counts = mapOf(d2 to 2, t19 to 2, t20 to 4, BoardZone(1, Ring.SINGLE) to 1),
            misses = 1,
            total = 10,
            includesCricket = false,
        )

        val top = heatmap.topZones(3)

        // Ties (2 and 2): BoardZone.ALL order puts the double of 2 before the triple of 19.
        assertEquals(listOf(t20, d2, t19), top.map { it.zone })
        assertEquals(listOf(4, 2, 2), top.map { it.count })
        assertEquals(listOf(40.0, 20.0, 20.0), top.map { it.percent })
        assertEquals(4, heatmap.topZones().size)
    }

    @Test
    fun `ST10 one corrupt match among valid ones is skipped, the valid ones are counted alone`() {
        val valid = StatsMatchFixtures.x01Raw(players = 2, sessionId = "ok")
        val corrupt = StatsMatchFixtures.x01Raw(players = 2, sessionId = "bad").copy(settings = null)

        val matches = StatsMatchValidator.validateAll(listOf(valid, corrupt))

        assertEquals(1, matches.ignoredCount)
        val heatmap = heatmapOf(matches.matches, 1L, StatsFilter.AllModes)
        assertEquals(6, heatmap.total)
        assertEquals(4, heatmap.counts[t20])
    }

    @Test
    fun `ST2 only the matches handed over are counted, a reset removes the earlier ones`() {
        val old = StatsMatchFixtures.x01(sessionId = "old", startedAt = 10, endedAt = 20)
        val recent = StatsMatchFixtures.x01(sessionId = "new", startedAt = 500, endedAt = 600)

        val selected = select(listOf(old, recent), 1L, 100, StatsFilter.AllModes, Period.SINCE_ALWAYS, now = 1_000)

        assertEquals(6, heatmapOf(selected, 1L, StatsFilter.AllModes).total)
    }

    // ---- last leg from the journal ----

    private val names = listOf("A", "B")
    private val x01Setup = LegSetup.X01(X01Config(names, startScore = 301))
    private val cricketSetup = LegSetup.Cricket(CricketConfig(names))

    private fun MatchJournal.throwAll(vararg darts: Dart): MatchJournal =
        darts.fold(this) { journal, dart -> journal.enter(dart, at = 1L) }

    private val threeMisses = arrayOf(Dart.MISS, Dart.MISS, Dart.MISS)
    private val bigVisit = arrayOf(Dart.triple(20), Dart.triple(20), Dart.triple(20))

    /**
     * A finished 301 leg: the starter wins with T20 x3, then T20, T19, D2 (the other player misses in between). The
     * players are the starter and the other one, whoever they are.
     */
    private fun finishedLegByA(journal: MatchJournal): MatchJournal =
        journal.throwAll(*bigVisit).throwAll(*threeMisses).throwAll(Dart.triple(20), Dart.triple(19), Dart.double(2))

    @Test
    fun `HM9 end screen reads the current finished leg`() {
        val journal = finishedLegByA(MatchJournal("m", 0))

        val winner = lastLegHeatmap(journal, x01Setup, 0, LastLeg.CURRENT)
        val loser = lastLegHeatmap(journal, x01Setup, 1, LastLeg.CURRENT)

        assertEquals(mapOf(t20 to 4, t19 to 1, d2 to 1), winner.counts)
        assertEquals(6, winner.total)
        assertEquals(3, loser.misses)
        assertFalse(winner.includesCricket)
    }

    @Test
    fun `HM9 between legs reads the last completed leg, the next leg being empty`() {
        val journal = finishedLegByA(MatchJournal("m", 0)).startNextLeg()
        assertEquals(0, journal.currentLeg.events.size)

        val completed = lastLegHeatmap(journal, x01Setup, 0, LastLeg.COMPLETED)
        val current = lastLegHeatmap(journal, x01Setup, 0, LastLeg.CURRENT)

        assertEquals(6, completed.total)
        assertEquals(4, completed.counts[t20])
        assertEquals(0, current.total) // the empty next leg
    }

    @Test
    fun `HM9 only the last leg of a multi-leg match counts and its starter follows the leg number`() {
        val journal = finishedLegByA(finishedLegByA(MatchJournal("m", 0)).startNextLeg()).startNextLeg()
        // Leg 1 was started by player 1: B threw T20 x3 first, A missed in between.

        val b = lastLegHeatmap(journal, x01Setup, 1, LastLeg.COMPLETED)
        val a = lastLegHeatmap(journal, x01Setup, 0, LastLeg.COMPLETED)

        assertEquals(mapOf(t20 to 4, t19 to 1, d2 to 1), b.counts)
        assertEquals(3, a.misses)
        assertEquals(3, a.total)
    }

    @Test
    fun `HM9 undone darts are excluded and the winner's incomplete last visit is counted as thrown`() {
        val journal = MatchJournal("m", 0)
            .throwAll(*bigVisit).throwAll(*threeMisses)
            .throwAll(Dart.triple(20), Dart.triple(7), Dart.MISS) // 121 - 81 = 40 left
            .throwAll(*threeMisses)
            .throwAll(Dart.single(1)).undo() // undone dart, never counted
            .throwAll(Dart.double(20)) // the winner's last visit holds a single dart

        val heatmap = lastLegHeatmap(journal, x01Setup, 0, LastLeg.CURRENT)

        assertEquals(mapOf(t20 to 4, BoardZone(7, Ring.TRIPLE) to 1, BoardZone(20, Ring.DOUBLE) to 1), heatmap.counts)
        assertEquals(1, heatmap.misses)
        assertEquals(7, heatmap.total)
    }

    @Test
    fun `HM9 HM4 a Cricket journal flags Cricket and counts its misses`() {
        val journal = MatchJournal("m", 0)
            .throwAll(Dart.triple(20), Dart.double(19), Dart.MISS)

        val heatmap = lastLegHeatmap(journal, cricketSetup, 0, LastLeg.CURRENT)

        assertTrue(heatmap.includesCricket)
        assertEquals(3, heatmap.total)
        assertEquals(1, heatmap.counts[t20])
        assertEquals(1, heatmap.counts[BoardZone(19, Ring.DOUBLE)])
        assertEquals(1, heatmap.misses)
    }

    @Test
    fun `HM9 one to four players`() {
        for (count in 1..4) {
            val setup = LegSetup.X01(X01Config(List(count) { "P$it" }, startScore = 301))
            var journal = MatchJournal("m", 0)
            repeat(count) { journal = journal.throwAll(Dart.single(20), Dart.single(20), Dart.single(20)) }

            for (player in 0 until count) {
                assertEquals(3, lastLegHeatmap(journal, setup, player, LastLeg.CURRENT).total)
            }
        }
    }

    @Test
    fun `HM9 HM7 missing leg, out-of-range player and empty journal give an empty heatmap`() {
        val fresh = MatchJournal("m", 0)
        assertEquals(Heatmap.EMPTY, lastLegHeatmap(fresh, x01Setup, 0, LastLeg.COMPLETED))
        assertEquals(Heatmap.EMPTY, lastLegHeatmap(fresh, x01Setup, 0, LastLeg.CURRENT))

        val journal = finishedLegByA(fresh)
        assertEquals(Heatmap.EMPTY, lastLegHeatmap(journal, x01Setup, 5, LastLeg.CURRENT))
        assertEquals(Heatmap.EMPTY, lastLegHeatmap(journal, x01Setup, -1, LastLeg.CURRENT))
    }

    @Test
    fun `ST10 HM9 a journal that cannot be replayed gives an empty heatmap`() {
        val journal = MatchJournal("m", 0).undo()
        assertEquals(Heatmap.EMPTY, lastLegHeatmap(journal, x01Setup, 0, LastLeg.CURRENT))
    }
}
