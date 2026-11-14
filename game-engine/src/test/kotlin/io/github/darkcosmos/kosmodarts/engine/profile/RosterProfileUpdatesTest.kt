package io.github.darkcosmos.kosmodarts.engine.profile

import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor.Companion.BLUE
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor.Companion.ORANGE
import io.github.darkcosmos.kosmodarts.engine.player.Roster
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** History rule PM9. */
class RosterProfileUpdatesTest {

    private val roster = Roster.of(listOf("Ana", "Bob"), listOf(BLUE, ORANGE))

    @Test
    fun `PM9 rename gives the new spelling and keeps the color`() {
        assertEquals(Roster.of(listOf("ANA", "Bob"), listOf(BLUE, ORANGE)), roster.withProfileRenamed("ana", "ANA"))
    }

    @Test
    fun `PM9 merge with target absent renames the entry`() {
        assertEquals(Roster.of(listOf("Carl", "Bob"), listOf(BLUE, ORANGE)), roster.withProfileMerged("ana", "Carl"))
    }

    @Test
    fun `PM9 merge with target present removes the entry`() {
        assertEquals(Roster.of(listOf("Bob"), listOf(ORANGE)), roster.withProfileMerged("ana", "bob"))
    }

    @Test
    fun `PM9 delete removes the entry and keeps the others`() {
        assertEquals(Roster.of(listOf("Bob"), listOf(ORANGE)), roster.withProfileDeleted("ana"))
    }

    @Test
    fun `PM9 delete of the only entry leaves an empty name with its color`() {
        val single = Roster.of(listOf("Ana"), listOf(BLUE))
        assertEquals(Roster.of(listOf(""), listOf(BLUE)), single.withProfileDeleted("ana"))
    }

    @Test
    fun `PM9 unrelated profile leaves the roster unchanged`() {
        assertEquals(roster, roster.withProfileDeleted("zed"))
        assertEquals(roster, roster.withProfileRenamed("zed", "Zed"))
    }
}
