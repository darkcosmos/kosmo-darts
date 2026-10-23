package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CricketMarksTest {

    private val withBull = CricketConfig(listOf("Anna"))
    private val withoutBull = CricketConfig(listOf("Anna"), isBullInPlay = false)

    @Test
    fun `CR2 single, double and triple of 15 to 20 give 1, 2 and 3 marks on that number`() {
        listOf(withBull, withoutBull).forEach { config ->
            (15..20).forEach { number ->
                val target = CricketTarget.entries.single { it.points == number }
                assertEquals(CricketMarks(target, 1), CricketMarks.of(Dart.single(number), config))
                assertEquals(CricketMarks(target, 2), CricketMarks.of(Dart.double(number), config))
                assertEquals(CricketMarks(target, 3), CricketMarks.of(Dart.triple(number), config))
            }
        }
    }

    @Test
    fun `CR2 25 gives 1 mark and Bull 2 marks on the Bull`() {
        assertEquals(CricketMarks(CricketTarget.BULL, 1), CricketMarks.of(Dart.OUTER_BULL, withBull))
        assertEquals(CricketMarks(CricketTarget.BULL, 2), CricketMarks.of(Dart.BULL, withBull))
    }

    @Test
    fun `CR2 1 to 14 in every ring and a miss give no mark`() {
        listOf(withBull, withoutBull).forEach { config ->
            (1..14).forEach { number ->
                Multiplier.entries.forEach { multiplier ->
                    assertEquals(CricketMarks.NONE, CricketMarks.of(Dart(number, multiplier), config))
                }
            }
            assertEquals(CricketMarks.NONE, CricketMarks.of(Dart.MISS, config))
        }
    }

    @Test
    fun `CR2 25 and Bull give no mark with 25-Bull off`() {
        assertEquals(CricketMarks.NONE, CricketMarks.of(Dart.OUTER_BULL, withoutBull))
        assertEquals(CricketMarks.NONE, CricketMarks.of(Dart.BULL, withoutBull))
    }

    @Test
    fun `CR2 marks are 1 to 3 on a target and 0 without one`() {
        assertThrows<IllegalArgumentException> { CricketMarks(CricketTarget.TWENTY, 0) }
        assertThrows<IllegalArgumentException> { CricketMarks(CricketTarget.TWENTY, 4) }
        assertThrows<IllegalArgumentException> { CricketMarks(null, 1) }
    }
}
