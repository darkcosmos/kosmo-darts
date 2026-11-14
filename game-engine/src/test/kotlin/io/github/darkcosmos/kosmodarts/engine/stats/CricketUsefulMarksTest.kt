package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CricketUsefulMarksTest {

    private val miss = Dart.MISS
    private val standard2 = CricketConfig(listOf("A", "B"))
    private val cutThroat2 = CricketConfig(listOf("A", "B"), CricketVariant.CUT_THROAT)
    private val cutThroat3 = CricketConfig(listOf("A", "B", "C"), CricketVariant.CUT_THROAT)

    /** Pads [darts] with misses to the end of a three-dart turn. */
    private fun turn(vararg darts: Dart): List<Dart> = darts.toList() + List(3 - darts.size) { miss }

    private fun yields(config: CricketConfig, darts: List<Dart>): List<DartYield> {
        val result = usefulMarks(config, darts)
        assertTrue(result.isValid)
        return result.yields
    }

    private fun useful(useful: Int, scored: Int = 0, given: Int = 0) = DartYield(useful, scored, given)

    @Test
    fun `STC1 closing marks are useful, even when nobody has the target open`() {
        val darts = turn(Dart.triple(20)) + turn(Dart.triple(20)) + listOf(Dart.single(20))
        val result = yields(standard2, darts)
        assertEquals(useful(3), result[0])
        // B closes 20 too: it is dead for A and B now, so A's next single is on a dead target.
        assertEquals(useful(3), result[3])
        assertEquals(DartYield.NONE, result[6])
    }

    @Test
    fun `STC1 STC4 Standard surplus is useful and scores while an opponent has the target open`() {
        val result = yields(standard2, listOf(Dart.single(20), Dart.triple(20), miss))
        assertEquals(useful(1), result[0])
        assertEquals(useful(3, scored = 20), result[1])
    }

    @Test
    fun `STC1 STC4 Cut-throat surplus gives target value times open opponents`() {
        val result = yields(cutThroat3, listOf(Dart.triple(20), Dart.triple(20), miss))
        assertEquals(useful(3), result[0])
        assertEquals(useful(3, given = 60 * 2), result[1])
    }

    @Test
    fun `STC4 20 given to 2 opponents counts 40`() {
        val result = yields(cutThroat3, listOf(Dart.triple(20), Dart.single(20), miss))
        assertEquals(useful(1, given = 40), result[1])
    }

    @Test
    fun `STC4 Cut-throat counts only the opponents who still have the target open`() {
        // B closes 20, then A closes it and overshoots: only C is open.
        val darts = turn() + turn(Dart.triple(20)) + turn() + turn(Dart.triple(20), Dart.single(20))
        val result = yields(cutThroat3, darts)
        assertEquals(useful(1, given = 20), result[10])
    }

    @Test
    fun `STC1 a mark on a dead target is not useful, a closing triple that makes it dead gives 1`() {
        val darts = turn(Dart.triple(20)) + turn(Dart.double(20), Dart.single(20)) + turn(Dart.single(20))
        val result = yields(standard2, darts)
        assertEquals(useful(3), result[0])
        // B: 2 marks then 1 closing; A closed it, B closes it: dead.
        assertEquals(useful(2), result[3])
        assertEquals(useful(1), result[4])
        // A's next dart on the dead 20 yields nothing.
        assertEquals(DartYield.NONE, result[6])
    }

    @Test
    fun `STC1 a triple closing with 1 mark when every opponent closed gives 1 useful mark and no points`() {
        val darts = turn(Dart.triple(20)) + turn(Dart.triple(20)) + turn() + turn() + turn(Dart.triple(20))
        // A and B both closed 20 already: A's triple is on a dead target.
        assertEquals(DartYield.NONE, yields(standard2, darts)[12])
    }

    @Test
    fun `STC1 a closing triple makes the target dead with 1 closing mark and a useless surplus`() {
        val darts = turn(Dart.triple(20)) + turn(Dart.double(20)) + turn(miss) + listOf(Dart.triple(20))
        val result = yields(standard2, darts)
        assertEquals(useful(1), result[9])
        assertEquals(0, result[9].pointsScored)
    }

    @Test
    fun `STC1 solo only closing marks are useful and nothing scores`() {
        val solo = CricketConfig(listOf("A"))
        val result = yields(solo, listOf(Dart.double(20), Dart.triple(20), Dart.single(20)))
        assertEquals(useful(2), result[0])
        assertEquals(useful(1), result[1])
        assertEquals(DartYield.NONE, result[2])
    }

    @Test
    fun `STC1 with the Bull in play 25 gives 1 mark and Bull 2`() {
        val result = yields(standard2, listOf(Dart.OUTER_BULL, Dart.BULL, Dart.BULL))
        assertEquals(useful(1), result[0])
        assertEquals(useful(2), result[1])
        // 3 marks so far, the third dart adds a surplus of 2 on the bull while B has it open: 2 x 25.
        assertEquals(useful(2, scored = 50), result[2])
    }

    @Test
    fun `STC1 with the Bull off the dart is listed and gives nothing`() {
        val config = CricketConfig(listOf("A", "B"), isBullInPlay = false)
        val result = yields(config, listOf(Dart.OUTER_BULL, Dart.BULL, miss))
        assertEquals(listOf(DartYield.NONE, DartYield.NONE, DartYield.NONE), result)
    }

    @Test
    fun `STC1 misses and 1 to 14 give 0`() {
        val darts = (1..14).map { Dart.triple(it) }.take(3) + listOf(Dart.double(14), Dart.single(1), miss)
        val result = yields(standard2, darts)
        assertEquals(6, result.size)
        assertTrue(result.all { it == DartYield.NONE })
    }

    @Test
    fun `STC1 STC4 works with 2, 3 and 4 players in both variants`() {
        listOf(2, 3, 4).forEach { count ->
            val names = (1..count).map { "P$it" }
            val darts = listOf(Dart.triple(19), Dart.triple(19), Dart.single(19))
            val opponents = count - 1
            val standard = yields(CricketConfig(names), darts)
            assertEquals(useful(3, scored = 57), standard[1])
            assertEquals(useful(1, scored = 19), standard[2])
            val cut = yields(CricketConfig(names, CricketVariant.CUT_THROAT), darts)
            assertEquals(useful(1, given = 19 * opponents), cut[2])
        }
    }

    @Test
    fun `CR9 a dart after the win makes the result invalid without throwing`() {
        val solo = CricketConfig(listOf("A"), isBullInPlay = false)
        val closing = listOf(20, 19, 18, 17, 16, 15).map { Dart.triple(it) }
        val result = usefulMarks(solo, closing + Dart.single(20))
        assertFalse(result.isValid)
        assertEquals(6, result.yields.size)
        assertTrue(usefulMarks(solo, closing).isValid)
    }

    @Test
    fun `a start player out of range makes the result invalid without throwing`() {
        assertFalse(usefulMarks(standard2, listOf(Dart.single(20)), startingPlayerIndex = 2).isValid)
        assertFalse(usefulMarks(standard2, listOf(Dart.single(20)), startingPlayerIndex = -1).isValid)
    }

    @Test
    fun `CR13 the starting player of a leg throws the first darts`() {
        val result = usefulMarks(standard2, listOf(Dart.triple(20), Dart.triple(20)), startingPlayerIndex = 1)
        assertTrue(result.isValid)
        assertEquals(listOf(useful(3), useful(3, scored = 60)), result.yields)
    }

    @Test
    fun `an empty leg is valid and has no yield`() {
        assertEquals(CricketYields(emptyList(), isValid = true), usefulMarks(cutThroat2, emptyList()))
    }
}
