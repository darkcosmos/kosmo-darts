package io.github.darkcosmos.kosmodarts.engine.stats

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StatsMatchValidatorTest {

    private fun x01Variants(): List<RawStatsMatch> = listOf(501, 301).flatMap { score ->
        CheckoutRule.entries.map { StatsMatchFixtures.x01Raw(startScore = score, checkoutRule = it) }
    }

    private fun cricketVariants(): List<RawStatsMatch> =
        CricketVariant.entries.map { StatsMatchFixtures.cricketRaw(variant = it) }

    private fun allVariants(): List<RawStatsMatch> = x01Variants() + cricketVariants()

    private fun assertInvalid(reason: InvalidReason, raw: RawStatsMatch) {
        assertEquals(StatsValidation.Invalid(reason), StatsMatchValidator.validate(raw), "$reason for ${raw.settings}")
    }

    private fun assertInvalidEverywhere(reason: InvalidReason, corrupt: (RawStatsMatch) -> RawStatsMatch) {
        allVariants().forEach { assertInvalid(reason, corrupt(it)) }
    }

    @Test
    fun `ST10 valid X01 and Cricket matches of 1 to 4 players are accepted`() {
        for (players in 1..4) {
            allVariants().forEach { raw ->
                val fresh = when (val settings = raw.settings) {
                    is ArchivedSettings.X01 ->
                        StatsMatchFixtures.x01Raw(players, settings.startScore, settings.checkoutRule)
                    is ArchivedSettings.Cricket ->
                        StatsMatchFixtures.cricketRaw(players, settings.cricketVariant)
                    null -> raw
                }
                assertTrue(StatsMatchValidator.validate(fresh) is StatsValidation.Valid, "$players $fresh")
            }
        }
    }

    @Test
    fun `ST10 X01 per-dart remaining and bust come from the replay`() {
        val match = StatsMatchFixtures.x01(players = 2, startScore = 301)
        val first = match.dartsOf(0)
        assertEquals(listOf(301, 241, 181), first.take(3).map { it.remainingBefore })
        // After the 180, the other player misses three times, then 121 -> 61 -> 4 -> double 2.
        assertEquals(listOf(121, 61, 4), first.drop(3).map { it.remainingBefore })
        assertEquals(listOf(301, 301, 301), match.dartsOf(1).map { it.remainingBefore })
        assertTrue(match.dartsOf(0).none { it.isBust })
        assertTrue(match.dartsOf(0).all { it.yield == null })
    }

    @Test
    fun `ST10 a bust restores the remaining and is flagged`() {
        val t20 = Dart.triple(20)
        // 301 double out, solo: 180, then 121 - 60 - 60 leaves 1 (bust on the second dart), then 121 - 60 - 57 - D2.
        val visits = listOf(
            listOf(t20, t20, t20),
            listOf(t20, t20),
            listOf(t20, Dart.triple(19), Dart.double(2)),
        )
        val base = StatsMatchFixtures.x01Raw(players = 1, startScore = 301)
        var visit = 0
        val darts = visits.flatMap { v ->
            visit += 1
            v.mapIndexed { rank, d -> RawStatsDart(0, 0, visit, rank + 1, d.segment, d.multiplier, isVoided = false) }
        }
        val match = StatsMatchFixtures.valid(base.copy(darts = darts))
        val dartsOf = match.dartsOf(0)
        assertEquals(listOf(301, 241, 181, 121, 61, 121, 61, 4), dartsOf.map { it.remainingBefore })
        assertEquals(listOf(false, false, false, false, true, false, false, false), dartsOf.map { it.isBust })
    }

    @Test
    fun `ST10 Cricket per-dart yields come from the replay`() {
        val match = StatsMatchFixtures.cricket(players = 2)
        val yields = match.dartsOf(0).map { it.yield }
        assertEquals(DartYield(useful = 3, pointsScored = 0, pointsGiven = 0), yields.first())
        assertEquals(8, yields.size)
        assertTrue(match.dartsOf(0).all { it.remainingBefore == null && !it.isBust })
        assertEquals(DartYield.NONE, match.dartsOf(1).first().yield)
    }

    @Test
    fun `ST1 DJ2 voided darts are absent from dartsOf and do not change the replay`() {
        val raw = StatsMatchFixtures.x01Raw(players = 2)
        val voided = raw.darts.first().copy(segment = 5, multiplier = Multiplier.SINGLE, isVoided = true)
        val withVoided = raw.copy(darts = listOf(voided) + raw.darts)
        val plain = StatsMatchFixtures.valid(raw)
        val match = StatsMatchFixtures.valid(withVoided)
        assertEquals(plain.dartsOf(0), match.dartsOf(0))
        assertTrue(match.dartsOf(0).none { it.dart == Dart.single(5) })
        assertEquals(plain.dartsOf(1).size, match.dartsOf(1).size)
    }

    @Test
    fun `ST1 ST10 a leg whose darts are all voided is a leg without darts`() {
        val raw = StatsMatchFixtures.x01Raw(players = 1)
        assertInvalid(InvalidReason.LEG_WITHOUT_DARTS, raw.copy(darts = raw.darts.map { it.copy(isVoided = true) }))
    }

    @Test
    fun `ST10 settings that do not decode are rejected`() {
        assertInvalidEverywhere(InvalidReason.SETTINGS_UNDECODABLE) { it.copy(settings = null) }
    }

    @Test
    fun `ST10 an unknown segment or multiplier is rejected`() {
        assertInvalidEverywhere(InvalidReason.UNKNOWN_DART) {
            it.copy(darts = listOf(it.darts.first().copy(segment = 21)) + it.darts.drop(1))
        }
        assertInvalidEverywhere(InvalidReason.UNKNOWN_DART) {
            it.copy(darts = listOf(it.darts.first().copy(segment = -1)) + it.darts.drop(1))
        }
        assertInvalidEverywhere(InvalidReason.UNKNOWN_DART) {
            it.copy(darts = listOf(it.darts.first().copy(multiplier = null)) + it.darts.drop(1))
        }
        assertInvalidEverywhere(InvalidReason.UNKNOWN_DART) {
            val bullTriple = it.darts.first().copy(segment = Dart.BULL_SEGMENT, multiplier = Multiplier.TRIPLE)
            it.copy(darts = listOf(bullTriple) + it.darts.drop(1))
        }
    }

    @Test
    fun `ST10 CR9 S5 a dart after the leg win is rejected, never replayed`() {
        assertInvalidEverywhere(InvalidReason.DART_REJECTED) {
            it.copy(darts = it.darts + it.darts.first().copy(position = 0))
        }
    }

    @Test
    fun `ST10 a leg without darts is rejected`() {
        assertInvalidEverywhere(InvalidReason.LEG_WITHOUT_DARTS) { it.copy(darts = emptyList()) }
        assertInvalidEverywhere(InvalidReason.NO_LEG) { it.copy(legs = emptyList(), darts = emptyList()) }
    }

    @Test
    fun `ST10 a dart of an unknown leg is rejected`() {
        assertInvalidEverywhere(InvalidReason.DART_LEG) {
            it.copy(darts = it.darts + it.darts.first().copy(legOrdinal = 7))
        }
    }

    @Test
    fun `ST10 a rank out of range is rejected`() {
        assertInvalidEverywhere(InvalidReason.PLAYER_RANK) {
            it.copy(players = listOf(it.players.first().copy(rank = 0)) + it.players.drop(1))
        }
        assertInvalidEverywhere(InvalidReason.PLAYER_RANK) {
            it.copy(players = listOf(it.players.first().copy(rank = it.players.size + 1)) + it.players.drop(1))
        }
    }

    @Test
    fun `ST10 a player position out of range or duplicated is rejected`() {
        assertInvalidEverywhere(InvalidReason.PLAYER_POSITION) {
            it.copy(players = listOf(it.players.first().copy(position = 2)) + it.players.drop(1))
        }
        assertInvalidEverywhere(InvalidReason.PLAYER_POSITION) {
            it.copy(players = it.players.map { player -> player.copy(position = 0) })
        }
        assertInvalidEverywhere(InvalidReason.PLAYER_POSITION) {
            it.copy(players = listOf(it.players.first().copy(position = -1)) + it.players.drop(1))
        }
    }

    @Test
    fun `ST10 no player or five players are rejected`() {
        assertInvalidEverywhere(InvalidReason.PLAYER_COUNT) { it.copy(players = emptyList()) }
        assertInvalidEverywhere(InvalidReason.PLAYER_COUNT) {
            it.copy(players = List(5) { index -> it.players.first().copy(position = index) })
        }
    }

    @Test
    fun `ST10 legs won out of range are rejected`() {
        assertInvalidEverywhere(InvalidReason.LEGS_WON) {
            it.copy(players = listOf(it.players.first().copy(legsWon = -1)) + it.players.drop(1))
        }
        assertInvalidEverywhere(InvalidReason.LEGS_WON) {
            it.copy(players = listOf(it.players.first().copy(legsWon = 2)) + it.players.drop(1))
        }
    }

    @Test
    fun `ST10 sets won out of range are rejected`() {
        assertInvalidEverywhere(InvalidReason.SETS_WON) {
            it.copy(players = listOf(it.players.first().copy(setsWon = -1)) + it.players.drop(1))
        }
        assertInvalidEverywhere(InvalidReason.SETS_WON) {
            it.copy(players = listOf(it.players.first().copy(setsWon = 3)) + it.players.drop(1))
        }
    }

    @Test
    fun `ST10 a stored leg winner different from the replay is rejected`() {
        assertInvalidEverywhere(InvalidReason.LEG_WINNER) {
            it.copy(legs = listOf(it.legs.first().copy(winnerPosition = 1)))
        }
    }

    @Test
    fun `ST10 a leg that the replay does not finish is rejected`() {
        assertInvalidEverywhere(InvalidReason.LEG_WINNER) { it.copy(darts = it.darts.dropLast(1)) }
    }

    @Test
    fun `ST10 a leg starter or winner position out of range is rejected`() {
        assertInvalidEverywhere(InvalidReason.LEG_STARTER) {
            it.copy(legs = listOf(it.legs.first().copy(starterPosition = 5)))
        }
        assertInvalidEverywhere(InvalidReason.LEG_STARTER) {
            it.copy(legs = listOf(it.legs.first().copy(winnerPosition = -1)))
        }
    }

    @Test
    fun `ST10 a stored thrower that the replay contradicts is rejected`() {
        assertInvalidEverywhere(InvalidReason.DART_THROWER) {
            it.copy(darts = listOf(it.darts.first().copy(position = 1)) + it.darts.drop(1))
        }
    }

    @Test
    fun `ST10 a stored leg ordinal duplicated is rejected`() {
        assertInvalidEverywhere(InvalidReason.LEG_ORDINAL) { it.copy(legs = it.legs + it.legs) }
    }

    @Test
    fun `ST10 an unsupported X01 start score is rejected`() {
        val raw = StatsMatchFixtures.x01Raw()
        val settings = raw.settings as ArchivedSettings.X01
        assertInvalid(InvalidReason.SETTINGS_UNDECODABLE, raw.copy(settings = settings.copy(startScore = 701)))
    }

    @Test
    fun `ST10 validating a list keeps the good matches unchanged and counts the others`() {
        val good1 = StatsMatchFixtures.x01Raw(sessionId = "a")
        val bad = StatsMatchFixtures.cricketRaw(sessionId = "b").copy(settings = null)
        val good2 = StatsMatchFixtures.cricketRaw(sessionId = "c")
        val result = StatsMatchValidator.validateAll(listOf(good1, bad, good2))
        assertEquals(listOf("a", "c"), result.matches.map { it.sessionId })
        assertEquals(1, result.ignoredCount)
        val alone = StatsMatchValidator.validateAll(listOf(good1, good2))
        assertEquals(alone.matches.map { it.dartsOf(0) }, result.matches.map { it.dartsOf(0) })
        assertEquals(0, alone.ignoredCount)
    }

    @Test
    fun `ST10 the validator never throws on a wildly corrupt match`() {
        val raw = StatsMatchFixtures.x01Raw(players = 3).copy(
            darts = List(40) { RawStatsDart(it % 3, it, -it, 9, it * 7, Multiplier.TRIPLE, it % 2 == 0) },
        )
        assertTrue(StatsMatchValidator.validate(raw) is StatsValidation.Invalid)
    }

    @Test
    fun `ST5 a one-player match is solo`() {
        assertTrue(StatsMatchFixtures.x01(players = 1).isSolo)
        assertFalse(StatsMatchFixtures.x01(players = 2).isSolo)
        assertNull(StatsMatchFixtures.x01(players = 2).playerOf(9L))
        assertEquals(1L, StatsMatchFixtures.x01(players = 2).playerOf(1L)?.profileId)
    }
}
