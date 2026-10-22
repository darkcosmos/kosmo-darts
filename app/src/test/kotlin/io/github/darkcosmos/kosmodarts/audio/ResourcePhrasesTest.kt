package io.github.darkcosmos.kosmodarts.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The spoken texts of the rules tables, in English and in French (docs/rules/announcements.md, AN8, AN25, E1). */
@RunWith(AndroidJUnit4::class)
class ResourcePhrasesTest {

    /** A fixed draw: each line goes through its variants in order. */
    private fun phrases(): Phrases =
        ResourcePhrases(ApplicationProvider.getApplicationContext<Context>().resources, PhraseVariants(FirstVariant))

    /** Every variant of a line, in order: a picker that cycles through them (never the last one drawn). */
    private fun allVariants(isGameStart: Boolean, count: Int): List<String> {
        var next = 0
        val cycling = ResourcePhrases(
            ApplicationProvider.getApplicationContext<Context>().resources,
            PhraseVariants { allowed -> allowed.first { it == next % count }.also { next++ } },
        )
        return List(count) { cycling.turnStart("Ann", isGameStart) }
    }

    private val everyScoringDart: List<Dart> =
        Dart.NUMBERS.flatMap { n -> Multiplier.entries.map { Dart(n, it) } } + Dart.OUTER_BULL + Dart.BULL

    @Test
    @Config(qualifiers = "en")
    fun `AN8 E1 AN27 English phrases`() {
        val phrases = phrases()
        assertEquals(
            listOf("Ann, you're throwing first.", "Ann to throw first.", "Game on! Ann throws first."),
            allVariants(isGameStart = true, count = 3),
        )
        assertEquals(
            listOf("Ann, your turn.", "Ann, you're up.", "Over to you, Ann.", "Ann, step up."),
            allVariants(isGameStart = false, count = 4),
        )
        assertEquals("You require 40.", phrases.checkoutSuffix(40))
        assertEquals("140", phrases.turnScore(140))
        assertEquals("No score", phrases.noScore())
        assertEquals("Game shot! Ann wins", phrases.win("Ann"))
        assertEquals("Leg for Ann", phrases.legWon("Ann"))
        assertEquals("Set for Ann", phrases.setWon("Ann"))
        assertEquals("Announcements on", phrases.announcementsOn())
    }

    @Test
    @Config(qualifiers = "fr")
    fun `AN8 E1 AN27 French phrases`() {
        val phrases = phrases()
        assertEquals(
            listOf("Ann, c'est toi qui commences.", "On commence avec Ann.", "Ann, à toi l'honneur."),
            allVariants(isGameStart = true, count = 3),
        )
        assertEquals(
            listOf("Ann, c'est ton tour.", "Ann, à toi de jouer.", "À toi, Ann.", "Ann, c'est à toi."),
            allVariants(isGameStart = false, count = 4),
        )
        assertEquals("Il te reste 40.", phrases.checkoutSuffix(40))
        assertEquals("140", phrases.turnScore(140))
        assertEquals("Pas de score", phrases.noScore())
        assertEquals("Victoire de Ann", phrases.win("Ann"))
        assertEquals("Manche pour Ann", phrases.legWon("Ann"))
        assertEquals("Set pour Ann", phrases.setWon("Ann"))
        assertEquals("Annonces activées", phrases.announcementsOn())
    }

    @Test
    @Config(qualifiers = "en")
    fun `AN25 English dart calls`() {
        val phrases = phrases()
        assertEquals("18", phrases.dartCall(Dart.single(18)))
        assertEquals("Double 20", phrases.dartCall(Dart.double(20)))
        assertEquals("Triple 20", phrases.dartCall(Dart.triple(20)))
        assertEquals("25", phrases.dartCall(Dart.OUTER_BULL))
        assertEquals("Bull", phrases.dartCall(Dart.BULL))
        assertNull(phrases.dartCall(Dart.MISS))
        everyScoringDart.forEach { assertFalse("$it", phrases.dartCall(it).isNullOrBlank()) }
    }

    @Test
    @Config(qualifiers = "fr")
    fun `AN25 French dart calls`() {
        val phrases = phrases()
        assertEquals("18", phrases.dartCall(Dart.single(18)))
        assertEquals("Double 20", phrases.dartCall(Dart.double(20)))
        assertEquals("Triple 20", phrases.dartCall(Dart.triple(20)))
        assertEquals("25", phrases.dartCall(Dart.OUTER_BULL))
        assertEquals("Bull", phrases.dartCall(Dart.BULL))
        assertNull(phrases.dartCall(Dart.MISS))
        everyScoringDart.forEach { assertFalse("$it", phrases.dartCall(it).isNullOrBlank()) }
    }

    @Test
    @Config(qualifiers = "de")
    fun `AN11 a phone in another language gets the English texts`() {
        assertEquals("Game shot! Ann wins", phrases().win("Ann"))
    }
}
