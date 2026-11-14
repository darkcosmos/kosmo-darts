package io.github.darkcosmos.kosmodarts.ui.profile

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/** ST6, ST9, ST10: the texts of the Profile, in English and in French (plurals, date, dash). */
@RunWith(AndroidJUnit4::class)
class ProfileTextTest {
    private val resources get() = RuntimeEnvironment.getApplication().resources

    /** 2026-03-15 at noon UTC: the same calendar day in every time zone the tests may run in. */
    private val resetMillis = 1_773_576_000_000L

    @Test
    @Config(qualifiers = "en")
    fun matchCountsAreEnglishPlurals() {
        assertEquals("0 matches", matchCountText(resources, 0))
        assertEquals("1 match", matchCountText(resources, 1))
        assertEquals("2 matches", matchCountText(resources, 2))
    }

    @Test
    @Config(qualifiers = "fr")
    fun matchCountsAreFrenchPlurals() {
        assertEquals("0 match", matchCountText(resources, 0))
        assertEquals("1 match", matchCountText(resources, 1))
        assertEquals("2 matchs", matchCountText(resources, 2))
    }

    @Test
    @Config(qualifiers = "en")
    fun legCountsAreEnglishPlurals() {
        assertEquals("1 leg", legCountText(resources, 1))
        assertEquals("2 legs", legCountText(resources, 2))
    }

    @Test
    @Config(qualifiers = "fr")
    fun legCountsAreFrenchPlurals() {
        assertEquals("1 manche", legCountText(resources, 1))
        assertEquals("2 manches", legCountText(resources, 2))
    }

    /** ST10 */
    @Test
    @Config(qualifiers = "en")
    fun ignoredMatchesNoticeIsAnEnglishPlural() {
        assertEquals("1 match ignored (unreadable data)", ignoredMatchesText(resources, 1))
        assertEquals("3 matches ignored (unreadable data)", ignoredMatchesText(resources, 3))
    }

    /** ST10 */
    @Test
    @Config(qualifiers = "fr")
    fun ignoredMatchesNoticeIsAFrenchPlural() {
        assertEquals("1 match ignoré (données illisibles)", ignoredMatchesText(resources, 1))
        assertEquals("3 matchs ignorés (données illisibles)", ignoredMatchesText(resources, 3))
    }

    /** ST9 */
    @Test
    @Config(qualifiers = "fr")
    fun sinceUsesDepuisLeWithAFrenchDate() {
        val text = sinceText(resources, ProfileSince.Date(resetMillis), Locale.FRENCH)
        assertTrue(text, text.startsWith("Depuis le "))
        assertTrue(text, text.contains("mars") && text.contains("2026"))
        assertEquals("Depuis toujours", sinceText(resources, ProfileSince.Always, Locale.FRENCH))
    }

    /** ST9 */
    @Test
    @Config(qualifiers = "en")
    fun sinceUsesSinceWithAnEnglishDate() {
        val text = sinceText(resources, ProfileSince.Date(resetMillis), Locale.US)
        assertTrue(text, text.startsWith("Since "))
        assertTrue(text, text.contains("Mar") && text.contains("2026"))
        assertEquals("Since always", sinceText(resources, ProfileSince.Always, Locale.US))
    }

    /** ST6 */
    @Test
    @Config(qualifiers = "en")
    fun aFigureThatCannotBeComputedIsADashNeverZero() {
        assertEquals("–", averageText(null, Locale.US, resources))
        assertEquals("–", mprText(null, Locale.US, resources))
        assertEquals("–", numberText(null, resources))
        assertEquals("–", percentText(null, Locale.US, resources))
        assertEquals("–", checkoutText(0, 0, null, Locale.US, resources))
    }

    @Test
    @Config(qualifiers = "en")
    fun figuresUseTheDecimalSeparatorOfTheLocale() {
        assertEquals("45.50", averageText(45.5, Locale.US, resources))
        assertEquals("45,50", averageText(45.5, Locale.FRANCE, resources))
        assertEquals("2.0", mprText(2.0, Locale.US, resources))
        assertEquals("50 %", percentText(0.5, Locale.US, resources))
        assertEquals("50 % (5/10)", checkoutText(5, 10, 0.5, Locale.US, resources))
    }
}
