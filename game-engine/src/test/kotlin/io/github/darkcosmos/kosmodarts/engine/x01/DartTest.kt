package io.github.darkcosmos.kosmodarts.engine.x01

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DartTest {

    /** D1: singles, doubles and triples of 1 to 20 are worth 1x, 2x and 3x the number. */
    @Test
    fun `D1 numbered darts are worth number times multiplier`() {
        for (number in 1..20) {
            assertEquals(number, Dart.single(number).value)
            assertEquals(number * 2, Dart.double(number).value)
            assertEquals(number * 3, Dart.triple(number).value)
        }
    }

    /** D1: outer bull is worth 25, bull 50, miss 0. */
    @Test
    fun `D1 outer bull bull and miss values`() {
        assertEquals(25, Dart.OUTER_BULL.value)
        assertEquals(50, Dart.BULL.value)
        assertEquals(0, Dart.MISS.value)
    }

    /** D1: numbers outside 1 to 20 do not exist on the board. */
    @Test
    fun `D1 numbers outside 1 to 20 are rejected`() {
        assertThrows<IllegalArgumentException> { Dart.single(0) }
        assertThrows<IllegalArgumentException> { Dart.single(21) }
        assertThrows<IllegalArgumentException> { Dart.double(-1) }
        assertThrows<IllegalArgumentException> { Dart.triple(25) }
        assertThrows<IllegalArgumentException> { Dart(segment = 19, multiplier = Multiplier.SINGLE).copy(segment = 22) }
    }

    /** D1: a miss cannot carry a double or triple multiplier. */
    @Test
    fun `D1 a doubled or tripled miss is rejected`() {
        assertThrows<IllegalArgumentException> { Dart(Dart.MISS_SEGMENT, Multiplier.DOUBLE) }
        assertThrows<IllegalArgumentException> { Dart(Dart.MISS_SEGMENT, Multiplier.TRIPLE) }
    }

    /** D1: every dart keeps its segment and multiplier. */
    @Test
    fun `D1 darts keep segment and multiplier`() {
        assertEquals(Dart(20, Multiplier.TRIPLE), Dart.triple(20))
        assertEquals(Dart(Dart.OUTER_BULL_SEGMENT, Multiplier.SINGLE), Dart.OUTER_BULL)
        assertEquals(Dart(Dart.BULL_SEGMENT, Multiplier.SINGLE), Dart.BULL)
        assertEquals(Dart(Dart.MISS_SEGMENT, Multiplier.SINGLE), Dart.MISS)
    }

    /** D2: triple bull and triple outer bull cannot be created. */
    @Test
    fun `D2 triple bull cannot be created`() {
        assertThrows<IllegalArgumentException> { Dart(Dart.BULL_SEGMENT, Multiplier.TRIPLE) }
        assertThrows<IllegalArgumentException> { Dart(Dart.OUTER_BULL_SEGMENT, Multiplier.TRIPLE) }
        assertThrows<IllegalArgumentException> { Dart.BULL.copy(multiplier = Multiplier.TRIPLE) }
    }

    /** D2: double outer bull (and double bull) cannot be created. */
    @Test
    fun `D2 double outer bull cannot be created`() {
        assertThrows<IllegalArgumentException> { Dart(Dart.OUTER_BULL_SEGMENT, Multiplier.DOUBLE) }
        assertThrows<IllegalArgumentException> { Dart.OUTER_BULL.copy(multiplier = Multiplier.DOUBLE) }
        assertThrows<IllegalArgumentException> { Dart(Dart.BULL_SEGMENT, Multiplier.DOUBLE) }
    }

    /** D3: bull counts as a double; outer bull, singles, triples and miss do not. */
    @Test
    fun `D3 bull counts as a double`() {
        assertTrue(Dart.BULL.countsAsDouble)
        assertTrue(Dart.double(20).countsAsDouble)
        assertFalse(Dart.OUTER_BULL.countsAsDouble)
        assertFalse(Dart.single(20).countsAsDouble)
        assertFalse(Dart.triple(20).countsAsDouble)
        assertFalse(Dart.MISS.countsAsDouble)
    }
}
