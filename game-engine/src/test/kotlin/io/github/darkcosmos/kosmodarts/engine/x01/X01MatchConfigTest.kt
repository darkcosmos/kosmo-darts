package io.github.darkcosmos.kosmodarts.engine.x01

import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot.SLOT_1
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot.SLOT_2
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot.SLOT_3
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot.SLOT_4
import io.github.darkcosmos.kosmodarts.engine.player.InvalidColorSlotsException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** PC1, PC5 (docs/rules/players.md): the color slots of a match configuration. */
class X01MatchConfigTest {

    private val three = X01Config(listOf("Ann", "Bob", "Cid"))

    /** PC5: without slots, each player gets the slot of his position. */
    @Test
    fun `PC5 default slots follow the positions`() {
        assertEquals(listOf(SLOT_1, SLOT_2, SLOT_3), X01MatchConfig(three).colorSlots)
    }

    /** PC5: any distinct slots, one per player, are accepted. */
    @Test
    fun `PC5 distinct slots in any order are accepted`() {
        val config = X01MatchConfig(three, MatchFormat(), listOf(SLOT_4, SLOT_1, SLOT_2))

        assertEquals(listOf(SLOT_4, SLOT_1, SLOT_2), config.colorSlots)
    }

    /** PC5: too few or too many slots are rejected. */
    @Test
    fun `PC5 wrong slot count is rejected`() {
        assertThrows<InvalidColorSlotsException> { X01MatchConfig(three, MatchFormat(), listOf(SLOT_1, SLOT_2)) }
        assertThrows<InvalidColorSlotsException> {
            X01MatchConfig(three, MatchFormat(), listOf(SLOT_1, SLOT_2, SLOT_3, SLOT_4))
        }
    }

    /** PC1, PC5: a repeated slot is rejected. */
    @Test
    fun `PC5 repeated slots are rejected`() {
        assertThrows<InvalidColorSlotsException> {
            X01MatchConfig(three, MatchFormat(), listOf(SLOT_1, SLOT_3, SLOT_1))
        }
    }
}
