package io.github.darkcosmos.kosmodarts.engine.player

import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor.Companion.BLUE
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor.Companion.LILAC
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor.Companion.ORANGE
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor.Companion.TEAL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

/** PC1 to PC4 (docs/rules/players.md). */
class RosterTest {

    private fun named(vararg names: String): Roster =
        names.foldIndexed(Roster.initial(names.size)) { index, roster, name -> roster.withName(index, name) }

    /** PC1: the initial roster gives the palette colors 1 to n by position. */
    @Test
    fun `PC1 initial roster has colors by position`() {
        assertEquals(listOf(BLUE), Roster.initial(1).colors)
        assertEquals(listOf(BLUE, ORANGE), Roster.initial(2).colors)
        assertEquals(listOf(BLUE, ORANGE, TEAL, LILAC), Roster.initial(4).colors)
        assertEquals(listOf("", ""), Roster.initial(2).names)
    }

    /** PC1: two entries never share a color, and a roster has 1 to 4 players. */
    @Test
    fun `PC1 shared colors and wrong sizes are rejected`() {
        assertThrows<InvalidPlayerColorsException> {
            Roster(listOf(RosterEntry("Ann", BLUE), RosterEntry("Bob", BLUE)))
        }
        assertThrows<InvalidPlayerColorsException> { Roster(emptyList()) }
        assertThrows<InvalidPlayerColorsException> { Roster.of(listOf("Ann", "Bob"), listOf(BLUE)) }
    }

    /** PC2: a new player gets the first free palette color in palette order. */
    @Test
    fun `PC2 add gives the first free color`() {
        val three = Roster.initial(2).withPlayerAdded()

        assertEquals(listOf(BLUE, ORANGE, TEAL), three.colors)
        assertEquals("", three.entries.last().name)
    }

    /** PC2: after removing color 2 of 4, the next player added gets color 2, at the end of the order. */
    @Test
    fun `PC2 add after removing color 2 of 4 gives color 2 back`() {
        val roster = named("Ann", "Bob", "Cid", "Dan").withPlayerRemoved(1).withPlayerAdded()

        assertEquals(listOf("Ann", "Cid", "Dan", ""), roster.names)
        assertEquals(listOf(BLUE, TEAL, LILAC, ORANGE), roster.colors)
    }

    /** PC3: removing the first, a middle or the last player frees his color; the others keep theirs. */
    @Test
    fun `PC3 remove at any position frees the color and keeps the others`() {
        val full = named("Ann", "Bob", "Cid", "Dan")

        assertEquals(listOf(ORANGE, TEAL, LILAC), full.withPlayerRemoved(0).colors)
        assertEquals(listOf("Bob", "Cid", "Dan"), full.withPlayerRemoved(0).names)
        assertEquals(listOf(BLUE, ORANGE, LILAC), full.withPlayerRemoved(2).colors)
        assertEquals(listOf("Ann", "Bob", "Dan"), full.withPlayerRemoved(2).names)
        assertEquals(listOf(BLUE, ORANGE, TEAL), full.withPlayerRemoved(3).colors)
    }

    /** PC2: up to 4 players, then add does nothing. */
    @Test
    fun `PC2 add up to 4 then canAdd is false`() {
        val full = Roster.initial(1).withPlayerAdded().withPlayerAdded().withPlayerAdded()

        assertEquals(4, full.size)
        assertFalse(full.canAdd)
        assertSame(full, full.withPlayerAdded())
    }

    /** PC3: down to 1 player, then remove does nothing. */
    @Test
    fun `PC3 remove down to 1 then canRemove is false`() {
        val one = named("Ann", "Bob").withPlayerRemoved(0)

        assertEquals(listOf("Bob"), one.names)
        assertEquals(listOf(ORANGE), one.colors)
        assertFalse(one.canRemove)
        assertTrue(one.canAdd)
        assertSame(one, one.withPlayerRemoved(0))
    }

    /** PC4: shuffle changes the order only; each name keeps its color. */
    @Test
    fun `PC4 shuffle keeps each name with its color`() {
        val roster = named("Ann", "Bob", "Cid", "Dan")
        val byColor = roster.entries.associate { it.name to it.color }

        val shuffled = roster.shuffled(Random(SEED))

        assertNotEquals(roster.names, shuffled.names)
        assertEquals(roster.entries.toSet(), shuffled.entries.toSet())
        shuffled.entries.forEach { assertEquals(byColor.getValue(it.name), it.color) }
    }

    /** PC1: a roster holding a color outside the palette (generated, T5) is valid; equal colors are not. */
    @Test
    fun `PC1 a color outside the palette is accepted, a repeated one is not`() {
        val roster = Roster.of(listOf("Ann", "Bob"), listOf(GENERATED, BLUE))

        assertEquals(listOf(GENERATED, BLUE), roster.colors)
        assertThrows<InvalidPlayerColorsException> { Roster.of(listOf("Ann", "Bob"), listOf(GENERATED, GENERATED)) }
    }

    /** PC2: a palette color too close to another player's generated color is skipped, in palette order. */
    @Test
    fun `PC2 add skips a palette color close to a generated color`() {
        val roster = Roster.of(listOf("Ann", "Bob"), listOf(BLUE, nearOf(ORANGE))).withPlayerAdded(Random(SEED))

        assertEquals(listOf(BLUE, nearOf(ORANGE), TEAL), roster.colors)
    }

    /** PC2, PC12: the color given next to three generated colors is acceptable against all of them. */
    @Test
    fun `PC2 add next to three generated colors gives an acceptable color`() {
        val taken = listOf(nearOf(BLUE), nearOf(ORANGE), nearOf(LILAC))

        val added = Roster.of(listOf("Ann", "Bob", "Cid"), taken).withPlayerAdded(Random(SEED))

        assertEquals(taken + TEAL, added.colors)
        assertTrue(PlayerColorRules.isAcceptable(added.colors.last(), taken))
    }

    /** PC3, PC4: removing and shuffling keep a color outside the palette with its player. */
    @Test
    fun `PC3 PC4 a generated color stays with its player`() {
        val roster = Roster.of(listOf("Ann", "Bob", "Cid"), listOf(BLUE, GENERATED, TEAL))

        assertEquals(listOf(GENERATED, TEAL), roster.withPlayerRemoved(0).colors)
        roster.shuffled(Random(SEED)).entries.forEach {
            assertEquals(roster.entries.first { entry -> entry.name == it.name }.color, it.color)
        }
    }

    /** PC1: a rename keeps the color. */
    @Test
    fun `PC1 rename keeps the color`() {
        val roster = Roster.initial(3).withPlayerRemoved(0).withName(1, "Cid")

        assertEquals(listOf(RosterEntry("", ORANGE), RosterEntry("Cid", TEAL)), roster.entries)
    }

    /** A color just off [base], within MIN_COLOR_DISTANCE of it, as a generated color could be. */
    private fun nearOf(base: PlayerColor): PlayerColor = PlayerColor(base.argb - 0x010101)

    private companion object {
        const val SEED = 1

        /** A light yellow outside the palette, standing for a color generated by T5's swatches. */
        val GENERATED = PlayerColor(0xFFF2D94E.toInt())
    }
}
