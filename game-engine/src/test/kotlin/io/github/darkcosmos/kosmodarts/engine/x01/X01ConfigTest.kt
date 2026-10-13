package io.github.darkcosmos.kosmodarts.engine.x01

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Locale

class X01ConfigTest {

    private fun assertRejected(vararg expected: X01ConfigError, build: () -> X01Config) {
        val exception = assertThrows<InvalidX01ConfigException> { build() }
        assertEquals(expected.toList(), exception.errors)
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

    /** C4: names are stored in NFC form: a decomposed "É" (E + combining accent) becomes the single letter "É". */
    @Test
    fun `C4 names are normalized to NFC`() {
        val config = X01Config(listOf(DECOMPOSED_ELODIE))
        assertEquals(listOf(COMPOSED_ELODIE), config.players)
        assertEquals(6, config.players.single().length)
    }

    /** C4: length is counted after NFC, so a decomposed accent does not count twice. */
    @Test
    fun `C4 decomposed and composed accented names have the same length`() {
        val composed = "a".repeat(19) + "\u00C9"
        val decomposed = "a".repeat(19) + "E\u0301"
        assertEquals(21, decomposed.codePointCount(0, decomposed.length))
        assertEquals(listOf(composed), X01Config(listOf(composed)).players)
        assertEquals(listOf(composed), X01Config(listOf(decomposed)).players)
        assertRejected(X01ConfigError.NAME_TOO_LONG) { X01Config(listOf("a$decomposed")) }
    }

    /** C4, C5: a composed and a decomposed spelling of the same name are duplicates. */
    @Test
    fun `C4 C5 composed and decomposed spellings are duplicates`() {
        assertRejected(X01ConfigError.DUPLICATE_NAME) { X01Config(listOf(COMPOSED_ELODIE, DECOMPOSED_ELODIE)) }
        assertRejected(X01ConfigError.DUPLICATE_NAME) { X01Config(listOf("\u00C9LODIE", DECOMPOSED_ELODIE)) }
    }

    /** C4: uniqueness ignores case only, not accents: "Elodie" and "Élodie" are two different players. */
    @Test
    fun `C4 names differing by an accent are not duplicates`() {
        assertEquals(listOf("Elodie", COMPOSED_ELODIE), X01Config(listOf("Elodie", COMPOSED_ELODIE)).players)
    }

    /** C4: an emoji made of several code points counts as each of its code points. */
    @Test
    fun `C4 multi code point emoji at the length limit`() {
        assertEquals(5, FAMILY_EMOJI.codePointCount(0, FAMILY_EMOJI.length))
        val atLimit = "a".repeat(15) + FAMILY_EMOJI
        assertEquals(listOf(atLimit), X01Config(listOf(atLimit)).players)
        assertRejected(X01ConfigError.NAME_TOO_LONG) { X01Config(listOf("a$atLimit")) }
    }

    /** C4: the duplicate check does not depend on the device language (Turkish has a special I/i rule). */
    @Test
    fun `C4 C5 case insensitive duplicates do not depend on the default locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertRejected(X01ConfigError.DUPLICATE_NAME) { X01Config(listOf("IVAN", "ivan")) }
            assertRejected(X01ConfigError.DUPLICATE_NAME) { X01Config(listOf("Irina", "iRINA")) }
            assertEquals(listOf("Ivan", "Tim"), X01Config(listOf("Ivan", "Tim")).players)
        } finally {
            Locale.setDefault(previous)
        }
    }

    /** C5: every broken rule is reported at once, in a stable order. */
    @Test
    fun `C5 several errors are reported together in a stable order`() {
        val allBroken = listOf(
            X01ConfigError.INVALID_START_SCORE,
            X01ConfigError.TOO_MANY_PLAYERS,
            X01ConfigError.BLANK_NAME,
            X01ConfigError.NAME_TOO_LONG,
            X01ConfigError.DUPLICATE_NAME,
        )
        val names = listOf("Alice", " ", "a".repeat(21), "ALICE", "Bob")
        assertRejected(*allBroken.toTypedArray()) { X01Config(names, startScore = 500) }
        assertRejected(*allBroken.toTypedArray()) { X01Config(names.reversed(), startScore = 500) }
    }

    /** C5: each broken rule is listed once, even when several names break it. */
    @Test
    fun `C5 each broken rule is listed once`() {
        assertRejected(X01ConfigError.BLANK_NAME, X01ConfigError.NAME_TOO_LONG) {
            X01Config(listOf("", " ", "a".repeat(21), "b".repeat(30)))
        }
        assertRejected(X01ConfigError.INVALID_START_SCORE, X01ConfigError.NO_PLAYERS) {
            X01Config(emptyList(), startScore = 0)
        }
    }

    /** C5: the error message names every broken rule. */
    @Test
    fun `C5 error message lists every broken rule`() {
        val exception = assertThrows<InvalidX01ConfigException> { X01Config(listOf(""), startScore = 1) }
        val message = exception.message.orEmpty()
        assertTrue("INVALID_START_SCORE" in message && "BLANK_NAME" in message, message)
    }

    private companion object {
        const val COMPOSED_ELODIE = "\u00C9lodie"
        const val DECOMPOSED_ELODIE = "E\u0301lodie"

        /** Man, zero width joiner, woman, zero width joiner, girl: one visible emoji, 5 code points. */
        const val FAMILY_EMOJI = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
    }
}
