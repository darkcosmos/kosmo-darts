package io.github.darkcosmos.kosmodarts.engine.cricket

import io.github.darkcosmos.kosmodarts.engine.player.PlayerListError
import io.github.darkcosmos.kosmodarts.engine.player.PlayerListViolation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CricketConfigTest {

    @Test
    fun `CR1 defaults are Standard with 25-Bull on`() {
        val config = CricketConfig(listOf("Anna"))
        assertEquals(CricketVariant.STANDARD, config.variant)
        assertTrue(config.isBullInPlay)
    }

    @Test
    fun `CR1 with 25-Bull on the targets are 20 down to 15 then the Bull`() {
        val targets = CricketConfig(listOf("Anna")).targets
        assertEquals(listOf(20, 19, 18, 17, 16, 15, 25), targets.map(CricketTarget::points))
        assertEquals(CricketTarget.BULL, targets.last())
        assertEquals(7, targets.size)
    }

    @Test
    fun `CR1 with 25-Bull off the targets are only 20 down to 15`() {
        val config = CricketConfig(listOf("Anna"), variant = CricketVariant.CUT_THROAT, isBullInPlay = false)
        assertEquals(CricketVariant.CUT_THROAT, config.variant)
        assertEquals(listOf(20, 19, 18, 17, 16, 15), config.targets.map(CricketTarget::points))
    }

    @Test
    fun `CR1 one to four players are accepted in order, trimmed`() {
        val config = CricketConfig(listOf(" Anna", "Bob ", "Cleo", "Dan"))
        assertEquals(listOf("Anna", "Bob", "Cleo", "Dan"), config.players)
    }

    @Test
    fun `CR1 C5 an invalid player list is rejected with every violation`() {
        val exception = assertThrows<InvalidCricketConfigException> {
            CricketConfig(listOf("Anna", " ", "anna", "Bob", "Cleo"))
        }
        assertEquals(
            listOf(
                PlayerListViolation(PlayerListError.TOO_MANY_PLAYERS),
                PlayerListViolation(PlayerListError.BLANK_NAME, listOf(1)),
                PlayerListViolation(PlayerListError.DUPLICATE_NAME, listOf(0, 2)),
            ),
            exception.violations,
        )
    }

    @Test
    fun `CR1 no players are rejected`() {
        val exception = assertThrows<InvalidCricketConfigException> { CricketConfig(emptyList()) }
        assertEquals(listOf(PlayerListViolation(PlayerListError.NO_PLAYERS)), exception.violations)
    }

    @Test
    fun `CR1 configs are equal by normalized players, variant and 25-Bull`() {
        assertEquals(CricketConfig(listOf("Anna ")), CricketConfig(listOf("Anna")))
        assertEquals(CricketConfig(listOf("Anna ")).hashCode(), CricketConfig(listOf("Anna")).hashCode())
        assertNotEquals(CricketConfig(listOf("Anna")), CricketConfig(listOf("Anna"), isBullInPlay = false))
        assertNotEquals(
            CricketConfig(listOf("Anna")),
            CricketConfig(listOf("Anna"), variant = CricketVariant.CUT_THROAT),
        )
    }
}
