package io.github.darkcosmos.kosmodarts.text

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The spoken words of a dart, shared by the voice announcements and the checkout suggestion description
 * (docs/rules/checkout-suggestions.md, CS9): the same in English and French.
 */
@RunWith(AndroidJUnit4::class)
class DartWordsTest {

    private fun words(dart: Dart): String? =
        dartWords(ApplicationProvider.getApplicationContext<Context>().resources, dart)

    private fun checkWords() {
        assertEquals("20", words(Dart.single(20)))
        assertEquals("Double 16", words(Dart.double(16)))
        assertEquals("Triple 20", words(Dart.triple(20)))
        assertEquals("25", words(Dart.OUTER_BULL))
        assertEquals("Bull", words(Dart.BULL))
        assertNull(words(Dart.MISS))
    }

    @Test
    @Config(qualifiers = "en")
    fun `CS9 English words`() = checkWords()

    @Test
    @Config(qualifiers = "fr")
    fun `CS9 French words`() = checkWords()
}
