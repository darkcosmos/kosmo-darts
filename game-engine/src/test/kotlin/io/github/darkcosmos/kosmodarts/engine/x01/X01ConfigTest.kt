package io.github.darkcosmos.kosmodarts.engine.x01

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class X01ConfigTest {

    private fun assertRejected(expected: X01ConfigError, build: () -> X01Config) {
        val exception = assertThrows<InvalidX01ConfigException> { build() }
        assertEquals(expected, exception.error)
    }

    /** C1, C2: defaults are 501 and double out. */
    @Test
    fun `C1 C2 defaults are 501 and double out`() {
        val config = X01Config(listOf("Alice"))
        assertEquals(501, config.startScore)
        assertEquals(CheckoutRule.DOUBLE_OUT, config.checkoutRule)
    }

    /** C1, C2: 301 and single out can be chosen. */
    @Test
    fun `C1 C2 301 and single out are accepted`() {
        val config = X01Config(listOf("Alice"), startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)
        assertEquals(301, config.startScore)
        assertEquals(CheckoutRule.SINGLE_OUT, config.checkoutRule)
        assertEquals(301, X01Game(config).player(0).remaining)
    }

    /** C1, C5: any start score other than 501 or 301 is rejected. */
    @Test
    fun `C1 C5 other start scores are rejected`() {
        listOf(0, 1, 101, 300, 500, 502, 701, -501).forEach { score ->
            assertRejected(X01ConfigError.INVALID_START_SCORE) { X01Config(listOf("Alice"), startScore = score) }
        }
    }

    /** C4: 1 to 4 players are accepted, in the given order. */
    @Test
    fun `C4 one to four players are accepted in order`() {
        assertEquals(listOf("Alice"), X01Config(listOf("Alice")).players)
        assertEquals(listOf("D", "C", "B", "A"), X01Config(listOf("D", "C", "B", "A")).players)
    }

    /** C4, C5: no player is rejected. */
    @Test
    fun `C4 C5 zero players are rejected`() {
        assertRejected(X01ConfigError.NO_PLAYERS) { X01Config(emptyList()) }
    }

    /** C4, C5: five players are rejected. */
    @Test
    fun `C4 C5 five players are rejected`() {
        assertRejected(X01ConfigError.TOO_MANY_PLAYERS) { X01Config(listOf("A", "B", "C", "D", "E")) }
    }

    /** C4: names are trimmed. */
    @Test
    fun `C4 names are trimmed`() {
        assertEquals(listOf("Alice", "Bob"), X01Config(listOf("  Alice ", "\tBob\n")).players)
    }

    /** C4, C5: empty or whitespace-only names are rejected. */
    @Test
    fun `C4 C5 blank names are rejected`() {
        assertRejected(X01ConfigError.BLANK_NAME) { X01Config(listOf("")) }
        assertRejected(X01ConfigError.BLANK_NAME) { X01Config(listOf("Alice", "   ")) }
    }

    /** C4: a name of exactly 20 characters after trimming is accepted. */
    @Test
    fun `C4 names of 20 characters are accepted`() {
        val name = "a".repeat(20)
        assertEquals(listOf(name), X01Config(listOf("  $name  ")).players)
    }

    /** C4, C5: a name of 21 characters is rejected. */
    @Test
    fun `C4 C5 names longer than 20 characters are rejected`() {
        assertRejected(X01ConfigError.NAME_TOO_LONG) { X01Config(listOf("a".repeat(21))) }
    }

    /** C4, C5: two names that differ only by case (or surrounding spaces) are rejected. */
    @Test
    fun `C4 C5 duplicate names ignoring case are rejected`() {
        assertRejected(X01ConfigError.DUPLICATE_NAME) { X01Config(listOf("Alice", "alice")) }
        assertRejected(X01ConfigError.DUPLICATE_NAME) { X01Config(listOf("Bob", "Alice", " ALICE ")) }
    }
}
