package io.github.darkcosmos.kosmodarts.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** U11: the check that a label only wraps between words, and the line budget of a key label. Pure JVM. */
class WordSafeTextTest {

    @Test
    fun `a break at a space is between words`() {
        assertTrue(breaksOnlyBetweenWords("JOUEUR SUIVANT", listOf(7, 14)))
        assertTrue(breaksOnlyBetweenWords("ANNULER", listOf(7)))
    }

    @Test
    fun `a break inside a word is refused`() {
        assertFalse(breaksOnlyBetweenWords("ANNULER", listOf(6, 7)))
        assertFalse(breaksOnlyBetweenWords("JOUEUR SUIVANT", listOf(5, 14)))
    }

    @Test
    fun `one line per word`() {
        assertEquals(1, wordCount("DOUBLE"))
        assertEquals(2, wordCount("✓ DOUBLE"))
        assertEquals(1, wordCount(""))
    }
}
