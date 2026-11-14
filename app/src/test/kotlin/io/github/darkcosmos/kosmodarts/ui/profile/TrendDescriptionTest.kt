package io.github.darkcosmos.kosmodarts.ui.profile

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.engine.stats.summarize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/** PS5: the TalkBack text of the trend curve, built from the engine summary, in English and in French. */
@RunWith(AndroidJUnit4::class)
class TrendDescriptionTest {
    private val resources get() = RuntimeEnvironment.getApplication().resources
    private val summary = summarize(TrendTestData.points(listOf(50.0, 40.0, 70.0, 60.0)))

    @Test
    @Config(qualifiers = "en")
    fun englishDescriptionReadsCountValuesAndDates() {
        val first = dateText(requireNotNull(summary.firstDate), Locale.ENGLISH)
        val last = dateText(requireNotNull(summary.lastDate), Locale.ENGLISH)
        assertEquals(
            "Trend curve of 4 matches. First 50.00, last 60.00, lowest 40.00, highest 70.00. From $first to $last.",
            trendDescription(summary, isCricket = false, Locale.ENGLISH, resources),
        )
        assertTrue(first.contains("2026"))
    }

    @Test
    @Config(qualifiers = "fr")
    fun frenchDescriptionReadsCountValuesAndDates() {
        val first = dateText(requireNotNull(summary.firstDate), Locale.FRENCH)
        val last = dateText(requireNotNull(summary.lastDate), Locale.FRENCH)
        assertEquals(
            "Courbe de tendance de 4 matchs. Premier 50,00, dernier 60,00, plus bas 40,00, plus haut 70,00. " +
                "Du $first au $last.",
            trendDescription(summary, isCricket = false, Locale.FRENCH, resources),
        )
        assertTrue(first.contains("mars"))
    }

    @Test
    @Config(qualifiers = "en")
    fun cricketValuesAreMarksPerRoundWithOneDecimal() {
        val cricket = summarize(TrendTestData.points(listOf(1.5, 2.25)))
        val text = trendDescription(cricket, isCricket = true, Locale.ENGLISH, resources)
        assertTrue(text, text.contains("First 1.5, last 2.3") || text.contains("First 1.5, last 2.2"))
        assertTrue(text, text.contains("lowest 1.5, highest 2."))
    }

    @Test
    @Config(qualifiers = "en")
    fun oneMatchIsSingularAndNoMatchHasItsOwnText() {
        val one = trendDescription(summarize(TrendTestData.points(listOf(48.0))), false, Locale.ENGLISH, resources)
        assertTrue(one, one.startsWith("Trend curve of 1 match. First 48.00, last 48.00, lowest 48.00, highest 48.00."))
        assertEquals(
            "Trend curve: no match.",
            trendDescription(summarize(emptyList()), false, Locale.ENGLISH, resources),
        )
    }

    @Test
    @Config(qualifiers = "fr")
    fun frenchNoMatchAndOneMatch() {
        assertEquals(
            "Courbe de tendance : aucun match.",
            trendDescription(summarize(emptyList()), false, Locale.FRENCH, resources),
        )
        val one = trendDescription(summarize(TrendTestData.points(listOf(48.0))), false, Locale.FRENCH, resources)
        assertTrue(one, one.startsWith("Courbe de tendance de 1 match. Premier 48,00"))
    }
}
