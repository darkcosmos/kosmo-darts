package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class X01StatsTest {

    private val miss = Dart.MISS
    private val t20 = Dart.triple(20)
    private val t19 = Dart.triple(19)

    /** A valid solo X01 match of one leg won with [visits] (each list is one visit of the player at position 0). */
    private fun solo(
        startScore: Int,
        rule: CheckoutRule = CheckoutRule.DOUBLE_OUT,
        visits: List<List<Dart>>,
    ): StatsMatch {
        val darts = visits.flatMapIndexed { index, visit ->
            visit.mapIndexed { rank, dart ->
                RawStatsDart(0, 0, index + 1, rank + 1, dart.segment, dart.multiplier, isVoided = false)
            }
        }
        val raw = RawStatsMatch(
            sessionId = "solo",
            startedAt = 1_000,
            endedAt = 2_000,
            settings = ArchivedSettings.X01(startScore, rule, MatchFormat(legsPerSet = 1, sets = 1)),
            players = listOf(RawStatsPlayer(profileId = 1, position = 0, rank = 1, legsWon = 1, setsWon = 1)),
            legs = listOf(RawStatsLeg(0, starterPosition = 0, winnerPosition = 0)),
            darts = darts,
        )
        return StatsMatchFixtures.valid(raw)
    }

    private fun figures(vararg matches: StatsMatch, profileId: Long = 1) = x01Figures(matches.toList(), profileId)

    /** 301: 180, then a miss-padded visit, then the double 2 (a 1-dart winning visit worth 4). */
    private val leg301OneDartWin = listOf(listOf(t20, t20, t20), listOf(t20, t19, miss), listOf(Dart.double(2)))

    @Test
    fun `STX1 STX2 STX4 STX7 a leg won in 7 darts uses 7 and a one-dart winning visit counts`() {
        val figures = figures(solo(301, visits = leg301OneDartWin))
        assertEquals(7, figures.bestLeg)
        assertEquals(7.0, figures.averageDartsPerLegWon)
        // 180 + 117 + 4 = 301 points in 7 darts.
        assertEquals(301.0 / 7 * 3, figures.average)
        assertEquals(301.0 / 7 * 3, figures.firstNineAverage)
        assertEquals(4, figures.bestCheckout)
    }

    @Test
    fun `STX4 a two-dart winning visit gives its total`() {
        val visits = listOf(listOf(t20, t20, t20), listOf(t20, miss, miss), listOf(Dart.triple(11), Dart.double(14)))
        assertEquals(61, figures(solo(301, visits = visits)).bestCheckout)
    }

    @Test
    fun `STX1 STX3 STX6 a bust visit scores 0, counts its darts and fails the attempt it contains`() {
        // 121 left, T20 T19 leaves 4, T20 busts at 4 (an attempt that fails), then 121 again and the double 2 wins.
        val visits = listOf(listOf(t20, t20, t20), listOf(t20, t19, t20), listOf(t20, t19, Dart.double(2)))
        val figures = figures(solo(301, visits = visits))
        assertEquals(1, figures.busts)
        assertEquals(301.0 / 9 * 3, figures.average)
        assertEquals(2, figures.checkoutAttempts)
        assertEquals(1, figures.checkoutSuccesses)
        assertEquals(0.5, figures.checkoutRatio)
        // The bust visit is excluded from the best visit and the bands (180 and 121 only).
        assertEquals(180, figures.bestVisit)
        assertEquals(1, figures.visits180)
        assertEquals(1, figures.visits100)
    }

    @Test
    fun `STX3 double out attempts are the even remainings from 2 to 40 and 50`() {
        val rule = CheckoutRule.DOUBLE_OUT
        listOf(40, 50, 2, 24).forEach { assertTrue(isCheckoutAttempt(it, rule), "$it") }
        listOf(41, 42, 1, 0, 52, 60, 39).forEach { assertFalse(isCheckoutAttempt(it, rule), "$it") }
    }

    @Test
    fun `STX3 single out attempts are the values of one dart, so 60 counts`() {
        val rule = CheckoutRule.SINGLE_OUT
        listOf(60, 57, 25, 50, 1, 20, 40, 39).forEach { assertTrue(isCheckoutAttempt(it, rule), "$it") }
        listOf(23, 61, 0, 59, 41).forEach { assertFalse(isCheckoutAttempt(it, rule), "$it") }
    }

    @Test
    fun `STX3 single out counts the remaining 60 reached mid-visit as an attempt`() {
        // 301: 180 leaves 121; T20 leaves 61 (not an attempt), S1 leaves 60 (an attempt), T20 wins in single out.
        val visits = listOf(listOf(t20, t20, t20), listOf(t20, Dart.single(1), t20))
        val figures = figures(solo(301, CheckoutRule.SINGLE_OUT, visits))
        assertEquals(1, figures.checkoutSuccesses)
        assertEquals(1, figures.checkoutAttempts)
        assertEquals(121, figures.bestCheckout)
    }

    @Test
    fun `STX5 bands are exclusive and the boundaries 59 60 99 100 139 140 and 180 fall in the right band`() {
        val first = solo(
            501,
            visits = listOf(
                listOf(t20, t20, t20),
                listOf(t20, t19, Dart.double(11)),
                listOf(t20, Dart.triple(13), miss),
                listOf(t19, Dart.single(2), miss),
                listOf(Dart.double(12)),
            ),
        )
        val second = solo(
            501,
            visits = listOf(
                listOf(t20, t20, Dart.double(10)),
                listOf(t20, Dart.double(20), miss),
                listOf(t20, miss, miss),
                listOf(t20, t19, t20),
                listOf(Dart.double(12)),
            ),
        )
        val figures = figures(first, second)
        assertEquals(1, figures.visits180)
        assertEquals(2, figures.visits140) // 140 and 177
        assertEquals(2, figures.visits100) // 100 and 139
        assertEquals(2, figures.visits60) // 60 and 99; 59 and 24 are in no band
        assertEquals(180, figures.bestVisit)
    }

    @Test
    fun `STX6 STX7 no leg won gives null averages, a loser has zero points but no null average`() {
        val loser = figures(StatsMatchFixtures.x01(), profileId = 2)
        assertNull(loser.averageDartsPerLegWon)
        assertNull(loser.bestLeg)
        assertNull(loser.bestCheckout)
        assertNull(loser.checkoutRatio)
        assertEquals(0.0, loser.average)
        assertEquals(0, loser.busts)
    }

    @Test
    fun `ST6 no match gives empty figures, an unknown profile too`() {
        val empty = figures()
        assertNull(empty.average)
        assertNull(empty.firstNineAverage)
        assertNull(empty.winRatio)
        assertEquals(0, empty.matchesWon)
        assertEquals(empty, figures(StatsMatchFixtures.x01(), profileId = 99))
    }

    @Test
    fun `STX2 first nine darts are taken per leg`() {
        // 501: three visits of 100 (the first 9 darts), then 180, then 1 and the double 10.
        val visit = listOf(t20, Dart.double(20), miss)
        val visits = listOf(visit, visit, visit, listOf(t20, t20, t20), listOf(Dart.single(1), Dart.double(10)))
        val figures = figures(solo(501, visits = visits))
        assertEquals(300.0 / 9 * 3, figures.firstNineAverage)
        assertEquals(501.0 / 14 * 3, figures.average)
    }

    @Test
    fun `STX8 ST5 results exclude the solo match and a rank 2 or 3 is a loss`() {
        val won = StatsMatchFixtures.x01(sessionId = "won")
        val lost = StatsMatchFixtures.x01(sessionId = "lost")
        val third = StatsMatchFixtures.valid(
            StatsMatchFixtures.x01Raw(players = 3, sessionId = "third").let { raw ->
                raw.copy(players = raw.players.map { if (it.position == 2) it.copy(rank = 3) else it })
            },
        )
        val solo = StatsMatchFixtures.x01(players = 1, sessionId = "solo")
        val winner = figures(won, solo)
        assertEquals(1, winner.matchesWon)
        assertEquals(0, winner.matchesLost)
        assertEquals(1.0, winner.winRatio)
        assertEquals(1, winner.legsWon)
        assertEquals(1, winner.setsWon)
        val loser = figures(lost, third, solo, profileId = 3)
        assertEquals(0, loser.matchesWon)
        assertEquals(1, loser.matchesLost)
        assertEquals(0.0, loser.winRatio)
        assertEquals(0, loser.legsWon)
        // A solo match counts in the averages but not in the results.
        val soloOnly = figures(solo)
        assertEquals(0, soloOnly.matchesWon + soloOnly.matchesLost)
        assertNull(soloOnly.winRatio)
        assertEquals(0, soloOnly.legsWon)
        assertEquals(0, soloOnly.setsWon)
        assertEquals(1, soloOnly.checkoutSuccesses)
    }

    @Test
    fun `STX8 legs and sets are the stored values, 501 and 301 alike`() {
        val raw = StatsMatchFixtures.x01Raw(startScore = 501, legs = 2, sessionId = "legs")
        val figures = figures(StatsMatchFixtures.valid(raw))
        assertEquals(2, figures.legsWon)
        assertEquals(1, figures.setsWon)
        assertEquals(1, figures.matchesWon)
    }

    @Test
    fun `STX3 a won leg is a success and the attempts are read from the stored remaining`() {
        val figures = figures(StatsMatchFixtures.x01(startScore = 501))
        assertEquals(1, figures.checkoutSuccesses)
        assertEquals(1, figures.checkoutAttempts)
        assertEquals(1.0, figures.checkoutRatio)
    }

    @Test
    fun `ST10 figures with one corrupt match among valid ones equal those of the valid ones alone`() {
        val valid = StatsMatchFixtures.x01Raw(sessionId = "ok")
        val corrupt = StatsMatchFixtures.x01Raw(sessionId = "bad").copy(settings = null)
        val kept = listOf(valid, corrupt)
            .mapNotNull { (StatsMatchValidator.validate(it) as? StatsValidation.Valid)?.match }
        assertEquals(1, kept.size)
        assertEquals(figures(StatsMatchFixtures.valid(valid)), x01Figures(kept, 1))
    }

    @Test
    fun `Cricket matches are ignored by the X01 figures`() {
        assertEquals(figures(), figures(StatsMatchFixtures.cricket()))
    }
}
