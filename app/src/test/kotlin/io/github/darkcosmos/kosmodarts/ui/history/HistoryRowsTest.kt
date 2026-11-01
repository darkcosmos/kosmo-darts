package io.github.darkcosmos.kosmodarts.ui.history

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** HS2: the texts of a History row, in the device language, date included. */
@RunWith(AndroidJUnit4::class)
class HistoryRowsTest {
    private val resources: Resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val utc: ZoneId = ZoneOffset.UTC

    private fun text(match: io.github.darkcosmos.kosmodarts.history.HistoryMatch, locale: Locale = Locale.US) =
        match.toRowText(resources, locale, utc)

    @Test
    fun `HS2 the date follows the device locale`() {
        val english = formatEndDate(NOON_MILLIS, Locale.US, utc)
        val french = formatEndDate(NOON_MILLIS, Locale.FRANCE, utc)
        assertTrue(english, english.contains("2026") && english.contains("Nov"))
        assertTrue(french, french.contains("2026") && french.contains("nov"))
        assertNotEquals(english, french)
    }

    @Test
    fun `HS2 the date is shown in the device time zone`() {
        val paris = formatEndDate(NOON_MILLIS, Locale.FRANCE, ZoneId.of("Europe/Paris"))
        // Noon UTC is 13:00 in Paris in November (UTC+1).
        assertTrue(paris, paris.contains("13:00"))
        assertFalse(paris, paris.contains("12:00"))
    }

    @Test
    fun `HS2 an X01 single game shows its mode and variant, the winner and no score`() {
        val row = text(x01HistoryMatch(1, "Ana", "Bob"))
        assertEquals("X01 · 501 · DOUBLE OUT", row.mode)
        assertEquals("Winner: Ana", row.winner)
        assertNull(row.score)
        assertEquals(listOf("Ana", "Bob"), row.players.map { it.name })
    }

    @Test
    fun `HS2 a Cricket match shows its variant`() {
        assertEquals("CRICKET · CUT-THROAT", text(cricketHistoryMatch(1, "Ana", "Bob")).mode)
    }

    @Test
    fun `HS2 players keep the color of the match`() {
        val row = text(x01HistoryMatch(1, "Ana", "Bob", "Cleo", "Dan"))
        assertEquals(listOf(BLUE, ORANGE, GREEN, PINK), row.players.map { it.colorArgb })
    }

    @Test
    fun `HS2 one to four players all appear`() {
        val names = listOf("Ana", "Bob", "Cleo", "Dan")
        (1..4).forEach { count ->
            val row = text(x01HistoryMatch(1, *names.take(count).toTypedArray()))
            assertEquals(names.take(count), row.players.map { it.name })
        }
        assertTrue(text(x01HistoryMatch(1, "Ana")).description.contains("Player: Ana"))
        assertTrue(text(x01HistoryMatch(1, "Ana", "Bob")).description.contains("Players: Ana, Bob"))
    }

    @Test
    fun `HS2 the legs score shows for several legs and the sets score for several sets`() {
        val legs = text(x01HistoryMatch(1, "Ana", "Bob", format = MatchFormat(legsPerSet = 2), legs = listOf(2, 1)))
        assertEquals("Legs 2–1", legs.score)
        val sets = text(
            x01HistoryMatch(
                1, "Ana", "Bob", format = MatchFormat(legsPerSet = 2, sets = 2),
                legs = listOf(4, 3), sets = listOf(2, 1),
            ),
        )
        assertEquals("Sets 2–1", sets.score)
    }

    @Test
    fun `HS2 one description reads date, mode, players, winner and score in this order`() {
        val match = x01HistoryMatch(1, "Ana", "Bob", format = MatchFormat(legsPerSet = 2), legs = listOf(2, 1))
        val row = text(match)
        assertEquals(
            listOf(row.date, row.mode, "Players: Ana, Bob", "Winner: Ana", "Legs 2–1").joinToString(". "),
            row.description,
        )
    }

    @Test
    fun `HS2 a tie for first place names every winner`() {
        val match = x01HistoryMatch(1, "Ana", "Bob")
        val tied = match.copy(players = match.players.map { it.copy(finalRank = 1) })
        assertEquals("Winners: Ana, Bob", text(tied).winner)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `HS2 the texts are French on a French device`() {
        val match = x01HistoryMatch(1, "Ana", "Bob", format = MatchFormat(legsPerSet = 2), legs = listOf(2, 1))
        val row = text(match, Locale.FRANCE)
        assertTrue(row.winner, row.winner.startsWith("Vainqueur"))
        assertEquals("Manches 2–1", row.score)
        assertTrue(row.description, row.description.contains("Joueurs"))
    }
}
