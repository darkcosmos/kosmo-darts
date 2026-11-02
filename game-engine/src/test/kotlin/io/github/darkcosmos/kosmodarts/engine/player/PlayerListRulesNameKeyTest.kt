package io.github.darkcosmos.kosmodarts.engine.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.Locale

/** The name key shared by the duplicate check (C4, C5, CR1) and the profiles (PF1). */
class PlayerListRulesNameKeyTest {

    @Test
    fun `C4 PF1 trim and case variants give the same key`() {
        assertEquals(PlayerListRules.nameKey("Kevin"), PlayerListRules.nameKey("  KEVIN "))
        assertEquals(PlayerListRules.nameKey("kEvIn"), PlayerListRules.nameKey("Kevin"))
    }

    @Test
    fun `C4 PF1 composed and decomposed accents give the same key`() {
        assertEquals(PlayerListRules.nameKey("Émile"), PlayerListRules.nameKey("Émile"))
    }

    @Test
    fun `C4 PF1 accents are kept and different names differ`() {
        assertNotEquals(PlayerListRules.nameKey("Emile"), PlayerListRules.nameKey("Émile"))
        assertNotEquals(PlayerListRules.nameKey("Ann"), PlayerListRules.nameKey("Anne"))
    }

    @Test
    fun `C4 PF1 the key does not depend on the default locale`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(PlayerListRules.nameKey("Kevin"), PlayerListRules.nameKey("KEVIN"))
            assertEquals("kevin", PlayerListRules.nameKey("KEVIN"))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `C4 PF1 letters outside the BMP fold case`() {
        assertEquals(PlayerListRules.nameKey("𐐀"), PlayerListRules.nameKey("𐐨"))
    }
}
