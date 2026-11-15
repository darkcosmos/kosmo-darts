package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketTarget
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CricketStatsTest {

    private val standard = CricketVariant.STANDARD
    private val cutThroat = CricketVariant.CUT_THROAT

    private fun figures(vararg matches: StatsMatch, profileId: Long = 1, variant: CricketVariant? = null) =
        cricketFigures(matches.toList(), profileId, variant)

    /**
     * A valid Cricket match of [players] players, one leg: player 0 throws [visits] (the others miss three times
     * between two of his visits) and wins by closing every target.
     */
    private fun custom(variant: CricketVariant, players: Int, visits: List<List<Dart>>): StatsMatch {
        val base = StatsMatchFixtures.cricketRaw(players, variant)
        val darts = ArrayList<RawStatsDart>()
        var visit = 0
        visits.forEachIndexed { index, list ->
            visit++
            list.forEachIndexed { rank, dart ->
                darts += RawStatsDart(0, 0, visit, rank + 1, dart.segment, dart.multiplier, isVoided = false)
            }
            if (index < visits.lastIndex) {
                for (other in 1 until players) {
                    visit++
                    repeat(3) { rank ->
                        darts += RawStatsDart(0, other, visit, rank + 1, 0, Multiplier.SINGLE, isVoided = false)
                    }
                }
            }
        }
        return StatsMatchFixtures.valid(base.copy(darts = darts))
    }

    private val closeRest = listOf(
        listOf(Dart.triple(18), Dart.triple(17), Dart.triple(16)),
        listOf(Dart.triple(15), Dart.BULL, Dart.OUTER_BULL),
    )

    @Test
    fun `STC2 MPR is useful marks x 3 over darts, across matches and legs, voided darts excluded`() {
        val raw = StatsMatchFixtures.cricketRaw(legs = 2)
        val voided = raw.darts.first().copy(segment = 20, multiplier = Multiplier.TRIPLE, isVoided = true)
        val twoLegs = StatsMatchFixtures.valid(raw.copy(darts = listOf(voided) + raw.darts))
        val oneLeg = StatsMatchFixtures.cricket(sessionId = "other")
        // Each leg: 21 useful marks in 8 darts for player 0 (the last visit has 2 darts).
        assertEquals(63.0 * 3 / 24, figures(twoLegs, oneLeg).mpr)
        assertEquals(0.0, figures(twoLegs, oneLeg, profileId = 2).mpr)
    }

    @Test
    fun `STC3 best visit is 9 at most and useful marks per target count 25 and Bull together`() {
        val figures = figures(StatsMatchFixtures.cricket())
        assertEquals(MAX_USEFUL_MARKS_PER_VISIT, figures.bestVisit)
        assertEquals(CricketTarget.entries.toSet(), figures.marksPerTarget.keys)
        assertEquals(3, figures.marksPerTarget.getValue(CricketTarget.TWENTY))
        assertEquals(3, figures.marksPerTarget.getValue(CricketTarget.FIFTEEN))
        // Bull (2 marks) and outer bull (1 mark) are one target.
        assertEquals(3, figures.marksPerTarget.getValue(CricketTarget.BULL))
    }

    @Test
    fun `STC4 Standard points scored are totalled and averaged per leg played`() {
        val visits = listOf(listOf(Dart.triple(20), Dart.single(20), Dart.triple(19))) + closeRest
        val figures = figures(custom(standard, 2, visits), StatsMatchFixtures.cricket(sessionId = "plain"))
        assertEquals(20, figures.pointsScored)
        assertEquals(10.0, figures.pointsScoredPerLeg)
        assertNull(figures.pointsGiven)
        assertNull(figures.pointsGivenPerLeg)
    }

    @Test
    fun `STC4 a surplus mark giving 20 points to 2 opponents counts 40, in Cut-throat only`() {
        val visits = listOf(listOf(Dart.triple(20), Dart.single(20), Dart.triple(19))) + closeRest
        val figures = figures(custom(cutThroat, 3, visits))
        assertEquals(40, figures.pointsGiven)
        assertEquals(40.0, figures.pointsGivenPerLeg)
        assertNull(figures.pointsScored)
    }

    @Test
    fun `STC5 All variants gives two result blocks, solo excluded`() {
        val won = StatsMatchFixtures.cricket(variant = standard, sessionId = "a")
        val lostCut = StatsMatchFixtures.cricket(variant = cutThroat, sessionId = "b")
        val solo = StatsMatchFixtures.valid(StatsMatchFixtures.cricketRaw(players = 1, sessionId = "c"))
        val asSecond = figures(won, lostCut, solo, profileId = 2)
        assertEquals(listOf(standard, cutThroat), asSecond.results.map { it.variant })
        assertEquals(CricketResults(standard, 0, 1, 0.0, 0, 0), asSecond.results[0])
        assertEquals(CricketResults(cutThroat, 0, 1, 0.0, 0, 0), asSecond.results[1])
        val asFirst = figures(won, solo, profileId = 1)
        assertEquals(CricketResults(standard, 1, 0, 1.0, 1, 1), asFirst.results[0])
        assertNull(asFirst.results[1].winRatio)
    }

    @Test
    fun `STC5 a variant filter gives a single result block`() {
        val figures = figures(StatsMatchFixtures.cricket(variant = cutThroat), variant = cutThroat)
        assertEquals(1, figures.results.size)
        assertEquals(cutThroat, figures.results.single().variant)
        assertEquals(1, figures.results.single().matchesWon)
    }

    @Test
    fun `ST6 no Cricket match gives no value`() {
        val figures = figures(StatsMatchFixtures.x01())
        assertNull(figures.mpr)
        assertNull(figures.bestVisit)
        assertNull(figures.pointsScored)
        assertNull(figures.pointsScoredPerLeg)
        assertNull(figures.pointsGiven)
        assertNull(figures.pointsGivenPerLeg)
        assertTrue(figures.marksPerTarget.values.all { it == 0 })
        assertTrue(figures.results.all { it.winRatio == null && it.matchesWon == 0 && it.matchesLost == 0 })
        assertEquals(figures, figures(StatsMatchFixtures.cricket(), profileId = 99))
    }

    @Test
    fun `ST10 figures with one corrupt match among valid ones equal those of the valid ones alone`() {
        val valid = StatsMatchFixtures.cricketRaw(sessionId = "ok")
        val corrupt = StatsMatchFixtures.cricketRaw(sessionId = "bad").copy(settings = null)
        val kept = listOf(valid, corrupt)
            .mapNotNull { (StatsMatchValidator.validate(it) as? StatsValidation.Valid)?.match }
        assertEquals(1, kept.size)
        assertEquals(figures(StatsMatchFixtures.valid(valid)), cricketFigures(kept, 1))
    }

    @Test
    fun `X01 matches are ignored by the Cricket figures`() {
        assertEquals(figures(), figures(StatsMatchFixtures.x01()))
    }
}
