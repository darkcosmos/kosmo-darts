package io.github.darkcosmos.kosmodarts.ui.history

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** HS5: the texts of the match detail, from the stored columns and string resources, in English and French. */
@RunWith(AndroidJUnit4::class)
class MatchDetailTextTest {
    private val resources: Resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun x01(rule: CheckoutRule, format: MatchFormat = MatchFormat()) = ArchivedSettings.X01(301, rule, format)

    @Test
    fun `HS5 the settings of an X01 match follow its finish mode`() {
        assertEquals(
            listOf("X01 · 301 · DOUBLE OUT"),
            settingsLines(resources, x01(CheckoutRule.DOUBLE_OUT)),
        )
        assertEquals(listOf("X01 · 301 · SINGLE OUT"), settingsLines(resources, x01(CheckoutRule.SINGLE_OUT)))
    }

    @Test
    fun `HS5 the format line is shown only for a match of several legs, singular and plural`() {
        val format = MatchFormat(legsPerSet = 3, sets = 2)
        assertEquals(
            listOf("X01 · 301 · DOUBLE OUT", "FIRST TO 2 SETS · FIRST TO 3 LEGS"),
            settingsLines(resources, x01(CheckoutRule.DOUBLE_OUT, format)),
        )
        assertEquals(1, settingsLines(resources, x01(CheckoutRule.DOUBLE_OUT)).size)
        assertEquals(
            "FIRST TO 2 LEGS",
            settingsLines(resources, x01(CheckoutRule.DOUBLE_OUT, MatchFormat(legsPerSet = 2, sets = 1)))[1],
        )
    }

    @Test
    fun `HS5 Cricket says when the 25-bull is out of play`() {
        val with = ArchivedSettings.Cricket(CricketVariant.CUT_THROAT, isBullInPlay = true, format = MatchFormat())
        val without = with.copy(isBullInPlay = false)
        assertEquals(1, settingsLines(resources, with).size)
        assertEquals(true, settingsLines(resources, without).single().endsWith("WITHOUT 25-BULL"))
    }

    @Test
    fun `HS5 the duration says hours and minutes with plurals, and under a minute`() {
        assertEquals("Duration: less than a minute", durationText(resources, 0))
        assertEquals("Duration: 1 minute", durationText(resources, 1))
        assertEquals("Duration: 47 minutes", durationText(resources, 47))
        assertEquals("Duration: 1 hour", durationText(resources, 60))
        assertEquals("Duration: 1 hour 1 minute", durationText(resources, 61))
        assertEquals("Duration: 2 hours 5 minutes", durationText(resources, 125))
    }

    @Test
    fun `HS5 PC9 the ranking is written by rank and name, with sets and legs for a match of several legs`() {
        val detail = x01Detail()
        val first = rankText(resources, detail.players.first(), detail)
        assertEquals("1. Ana", first.rank)
        assertEquals("1 set won · 3 legs won", first.score)

        val single = x01Detail(sets = 1, legsPerSet = 1)
        assertNull(rankText(resources, single.players.first(), single).score)
    }

    @Test
    fun `M19 the leg heading names the set only when the match has several`() {
        val legs = x01Detail(sets = 2, legsPerSet = 3).legs()
        assertEquals("SET 2 · LEG 3", legHeading(resources, legs.last()))
        assertEquals("LEG 2", legHeading(resources, x01Detail(sets = 1, legsPerSet = 3).legs()[1]))
    }

    @Test
    fun `HS5 darts are written as on the game screen, the total or BUST after them`() {
        val detail = x01Detail(sets = 1, legsPerSet = 1)
        val visits = detail.legs().single().visits
        assertEquals("T20 T20 T20", visitDartsText(resources, visits[0]))
        assertEquals("BULL MISS 19", visitDartsText(resources, visits[1]))
        assertEquals("180", visitTotalText(resources, visits[0], detail.settings))
        assertEquals("BUST", visitTotalText(resources, visits[1], detail.settings))
    }

    @Test
    fun `CR14 a Cricket visit shows marks with a plural`() {
        val detail = cricketDetail(isBullInPlay = false)
        val visit = detail.legs().single().visits.first()
        assertEquals("5 marks", visitTotalText(resources, visit, detail.settings))
        assertEquals("1 mark", visitTotalText(resources, visit.copy(total = 1), detail.settings))
    }

    @Test
    fun `U8 TalkBack reads the player, the darts in words, then the result`() {
        val detail = x01Detail(sets = 1, legsPerSet = 1)
        val visits = detail.legs().single().visits
        val first = visitDescription(resources, visits[0], detail.settings)
        assertEquals(true, first.startsWith("Ana: "))
        assertEquals(true, first.endsWith("Total 180"))
        assertEquals(true, visitDescription(resources, visits[1], detail.settings).endsWith("BUST"))
        assertEquals(true, visitDescription(resources, visits[1], detail.settings).contains("Miss"))
    }

    @Test
    @Config(qualifiers = "fr")
    fun `HS5 the French texts use the French plurals and wording`() {
        fun String.plain() = replace(' ', ' ')
        assertEquals("Durée : moins d'une minute", durationText(resources, 0).plain())
        assertEquals("Durée : 1 minute", durationText(resources, 1).plain())
        assertEquals("Durée : 2 heures 5 minutes", durationText(resources, 125).plain())
        val detail = x01Detail()
        assertEquals("SET 2 · MANCHE 3", legHeading(resources, detail.legs().last()))
        assertEquals(
            listOf("X01 · 501 · SORTIE DOUBLE", "1ER À 2 SETS · 1ER À 3 MANCHES"),
            settingsLines(resources, detail.settings).map { it.plain() },
        )
        val cricket = cricketDetail(isBullInPlay = false)
        assertEquals("5 marques", visitTotalText(resources, cricket.legs().single().visits.first(), cricket.settings))
        val spoken = visitDescription(resources, detail.legs().first().visits[1], detail.settings).plain()
        assertEquals("Bob : ", spoken.take("Bob : ".length))
        assertEquals(true, spoken.contains("Raté"))
    }
}
