package io.github.darkcosmos.kosmodarts.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The spoken texts of the rules table, in English and in French (docs/rules/announcements.md, AN8). */
@RunWith(AndroidJUnit4::class)
class ResourcePhrasesTest {

    private val phrases: Phrases
        get() = ResourcePhrases(ApplicationProvider.getApplicationContext<Context>().resources)

    private fun all(phrases: Phrases) = listOf(
        phrases.turnStart("Ann"),
        phrases.turnStartWithCheckout("Ann", 40),
        phrases.turnScore(140),
        phrases.oneEighty(),
        phrases.noScore(),
        phrases.bust(),
        phrases.gameShot("Ann"),
        phrases.bigFish("Ann"),
        phrases.announcementsOn(),
    )

    @Test
    @Config(qualifiers = "en")
    fun `AN8 English texts`() {
        assertEquals(
            listOf(
                "Ann",
                "Ann, you require 40",
                "140",
                "One hundred and eighty!",
                "No score",
                "Bust",
                "Game shot! Ann wins",
                "Big Fish! Ann wins",
                "Announcements on",
            ),
            all(phrases),
        )
    }

    @Test
    @Config(qualifiers = "fr")
    fun `AN8 French texts`() {
        assertEquals(
            listOf(
                "Ann",
                "Ann, il te reste 40",
                "140",
                "Cent quatre-vingts\u00A0!",
                "Pas de score",
                "Bust",
                "Victoire de Ann",
                "Big Fish\u00A0! Victoire de Ann",
                "Annonces activées",
            ),
            all(phrases),
        )
    }

    @Test
    @Config(qualifiers = "de")
    fun `AN11 a phone in another language gets the English texts`() {
        assertEquals("Ann, you require 40", phrases.turnStartWithCheckout("Ann", 40))
    }
}
