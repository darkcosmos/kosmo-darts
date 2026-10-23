package io.github.darkcosmos.kosmodarts.engine.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/** The player list rules shared by X01 (rules C4, C5) and Cricket (rule CR1). */
class PlayerListRulesTest {

    private fun errorsOf(vararg names: String): List<PlayerListError> =
        PlayerListRules.violations(names.toList()).map(PlayerListViolation::error)

    @Test
    fun `C4 CR1 one to four players are valid`() {
        (1..4).forEach { count ->
            val names = (1..count).map { "P$it" }
            assertTrue(PlayerListRules.violations(names).isEmpty(), "$count players")
        }
    }

    @Test
    fun `C4 C5 CR1 no players and five players are rejected`() {
        assertEquals(listOf(PlayerListViolation(PlayerListError.NO_PLAYERS)), PlayerListRules.violations(emptyList()))
        assertEquals(listOf(PlayerListError.TOO_MANY_PLAYERS), errorsOf("A", "B", "C", "D", "E"))
    }

    @Test
    fun `C4 names are trimmed and normalized to NFC`() {
        assertEquals(listOf("Alice", "\u00e9mile"), PlayerListRules.normalize(listOf("  Alice ", "e\u0301mile")))
    }

    @Test
    fun `C4 C5 blank names carry their positions and are never duplicates`() {
        assertEquals(
            listOf(PlayerListViolation(PlayerListError.BLANK_NAME, listOf(0, 2))),
            PlayerListRules.violations(listOf(" ", "Bob", "")),
        )
    }

    @Test
    fun `C4 C5 a name of 20 code points is valid and 21 is too long`() {
        assertTrue(errorsOf("a".repeat(PlayerListRules.MAX_NAME_LENGTH)).isEmpty())
        assertEquals(
            listOf(PlayerListViolation(PlayerListError.NAME_TOO_LONG, listOf(1))),
            PlayerListRules.violations(listOf("Bob", "a".repeat(PlayerListRules.MAX_NAME_LENGTH + 1))),
        )
    }

    @Test
    fun `C4 C5 duplicates ignore case but not accents, one violation per name`() {
        assertEquals(
            listOf(
                PlayerListViolation(PlayerListError.DUPLICATE_NAME, listOf(0, 2)),
                PlayerListViolation(PlayerListError.DUPLICATE_NAME, listOf(1, 3)),
            ),
            PlayerListRules.violations(listOf("Anna", "Bob", "ANNA", "bob")),
        )
        assertTrue(errorsOf("Emile", "\u00c9mile").isEmpty())
    }

    @Test
    fun `C4 duplicates do not depend on the default locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertEquals(listOf(PlayerListError.DUPLICATE_NAME), errorsOf("Ida", "IDA"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `C5 several errors are reported in the declaration order`() {
        assertEquals(
            listOf(
                PlayerListError.TOO_MANY_PLAYERS,
                PlayerListError.BLANK_NAME,
                PlayerListError.NAME_TOO_LONG,
                PlayerListError.DUPLICATE_NAME,
            ),
            errorsOf("Anna", "", "a".repeat(PlayerListRules.MAX_NAME_LENGTH + 1), "anna", "Bob"),
        )
    }
}
