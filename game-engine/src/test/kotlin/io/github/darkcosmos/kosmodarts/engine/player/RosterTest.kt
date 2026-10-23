package io.github.darkcosmos.kosmodarts.engine.player

import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot.SLOT_1
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot.SLOT_2
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot.SLOT_3
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot.SLOT_4
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

    /** PC1: the initial roster gives slots 1 to n by position. */
    @Test
    fun `PC1 initial roster has slots by position`() {
        assertEquals(listOf(SLOT_1), Roster.initial(1).slots)
        assertEquals(listOf(SLOT_1, SLOT_2), Roster.initial(2).slots)
        assertEquals(listOf(SLOT_1, SLOT_2, SLOT_3, SLOT_4), Roster.initial(4).slots)
        assertEquals(listOf("", ""), Roster.initial(2).names)
    }

    /** PC1: two entries never share a slot, and a roster has 1 to 4 players. */
    @Test
    fun `PC1 shared slots and wrong sizes are rejected`() {
        assertThrows<InvalidColorSlotsException> {
            Roster(listOf(RosterEntry("Ann", SLOT_1), RosterEntry("Bob", SLOT_1)))
        }
        assertThrows<InvalidColorSlotsException> { Roster(emptyList()) }
        assertThrows<InvalidColorSlotsException> { Roster.of(listOf("Ann", "Bob"), listOf(SLOT_1)) }
    }

    /** PC2: a new player gets the first free slot in slot order. */
    @Test
    fun `PC2 add gives the first free slot`() {
        val three = Roster.initial(2).withPlayerAdded()

        assertEquals(listOf(SLOT_1, SLOT_2, SLOT_3), three.slots)
        assertEquals("", three.entries.last().name)
    }

    /** PC2: after removing slot 2 of 4, the next player added gets slot 2, at the end of the order. */
    @Test
    fun `PC2 add after removing slot 2 of 4 gives slot 2 back`() {
        val roster = named("Ann", "Bob", "Cid", "Dan").withPlayerRemoved(1).withPlayerAdded()

        assertEquals(listOf("Ann", "Cid", "Dan", ""), roster.names)
        assertEquals(listOf(SLOT_1, SLOT_3, SLOT_4, SLOT_2), roster.slots)
    }

    /** PC3: removing the first, a middle or the last player frees his slot; the others keep theirs. */
    @Test
    fun `PC3 remove at any position frees the slot and keeps the others`() {
        val full = named("Ann", "Bob", "Cid", "Dan")

        assertEquals(listOf(SLOT_2, SLOT_3, SLOT_4), full.withPlayerRemoved(0).slots)
        assertEquals(listOf("Bob", "Cid", "Dan"), full.withPlayerRemoved(0).names)
        assertEquals(listOf(SLOT_1, SLOT_2, SLOT_4), full.withPlayerRemoved(2).slots)
        assertEquals(listOf("Ann", "Bob", "Dan"), full.withPlayerRemoved(2).names)
        assertEquals(listOf(SLOT_1, SLOT_2, SLOT_3), full.withPlayerRemoved(3).slots)
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
        assertEquals(listOf(SLOT_2), one.slots)
        assertFalse(one.canRemove)
        assertTrue(one.canAdd)
        assertSame(one, one.withPlayerRemoved(0))
    }

    /** PC4: shuffle changes the order only; each name keeps its slot. */
    @Test
    fun `PC4 shuffle keeps each name with its slot`() {
        val roster = named("Ann", "Bob", "Cid", "Dan")
        val bySlot = roster.entries.associate { it.name to it.slot }

        val shuffled = roster.shuffled(Random(SEED))

        assertNotEquals(roster.names, shuffled.names)
        assertEquals(roster.entries.toSet(), shuffled.entries.toSet())
        shuffled.entries.forEach { assertEquals(bySlot.getValue(it.name), it.slot) }
    }

    /** PC1: a rename keeps the slot. */
    @Test
    fun `PC1 rename keeps the slot`() {
        val roster = Roster.initial(3).withPlayerRemoved(0).withName(1, "Cid")

        assertEquals(listOf(RosterEntry("", SLOT_2), RosterEntry("Cid", SLOT_3)), roster.entries)
    }

    private companion object {
        const val SEED = 1
    }
}
