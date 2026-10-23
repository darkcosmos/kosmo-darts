package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R25 (ADR 0008): the frozen version 3 files of `src/test/resources/persistence/v3` (see its README), written by the
 * version 3 encoders before version 4, are still read as X01, the same as before.
 */
class SaveV3CompatibilityTest {

    private val gameCodec = GameSaveCodec()
    private val setupCodec = LastSetupCodec()

    @Test
    fun `R25 every version 3 saved game decodes to its X01 game`() {
        V3Fixtures.GAMES.forEach { (name, expected) ->
            val bytes = V3Fixtures.bytes(name)

            assertTrue(name, String(bytes, Charsets.UTF_8).startsWith("kosmodarts=saved-game\nversion=3\ngame=x01\n"))
            assertEquals(name, GameDecodeResult.Valid(expected), gameCodec.decode(bytes))
        }
    }

    /** R23, R25: a version 3 game is written back in version 4, with the X01 mode line, and stays the same game. */
    @Test
    fun `R25 a version 3 saved game re-encodes as version 4 with the X01 mode line`() {
        V3Fixtures.GAMES.forEach { (name, expected) ->
            val reencoded = checkNotNull(gameCodec.encode(expected))
            val lines = String(reencoded, Charsets.UTF_8).lines()

            assertEquals(name, listOf("kosmodarts=saved-game", "version=4", "mode=x01", "game=x01"), lines.take(4))
            assertEquals(name, GameDecodeResult.Valid(expected), gameCodec.decode(reencoded))
        }
    }

    @Test
    fun `R25 the version 3 last setup decodes to its X01 setup`() {
        val bytes = V3Fixtures.bytes("last-setup.save")

        assertEquals(V3Fixtures.LAST_SETUP, setupCodec.decode(bytes))
        assertFalse(String(bytes, Charsets.UTF_8).contains("mode="))
    }
}

/** The frozen version 3 files of `src/test/resources/persistence/v3`, with what they hold (see its README). */
internal object V3Fixtures {

    val GAMES: Map<String, SavedGame> = mapOf(
        "game-golden.save" to SaveFixtures.savedAfter(
            X01Config(listOf("Ann", "Clément")),
            listOf(Dart.triple(20), Dart.single(20), Dart.double(5)),
        ),
        "game-four-players-empty.save" to SavedGame(
            MatchFixtures.match("Ann", "Bob", "Cid", "Dan", startScore = 501, rule = CheckoutRule.DOUBLE_OUT),
            isAwaitingNextPlayer = false,
        ),
        "game-match-mid-leg.save" to SavedGame(
            MatchFixtures.match(
                "Ann",
                "Bob",
                "Cid",
                legsPerSet = 2,
                colorSlots = listOf(ColorSlot.SLOT_3, ColorSlot.SLOT_1, ColorSlot.SLOT_4),
            ).confirmLegsWonBy(1).throwing(Dart.triple(20)),
            isAwaitingNextPlayer = false,
        ),
        "game-between-legs.save" to SavedGame(
            MatchFixtures.match(
                "Ann",
                "Bob",
                legsPerSet = 3,
                sets = 2,
                colorSlots = listOf(ColorSlot.SLOT_2, ColorSlot.SLOT_4),
            ).confirmLegsWonBy(0, 1),
            isAwaitingNextPlayer = false,
            isBetweenLegs = true,
        ),
        "game-pending-match-win.save" to SavedGame(
            MatchFixtures.match("Ann", "Bob", legsPerSet = 2).confirmLegsWonBy(0).playLegWonBy(0),
            isAwaitingNextPlayer = true,
        ),
    )

    val LAST_SETUP: X01MatchConfig = X01MatchConfig(
        X01Config(listOf("Cid", "Ann", "Zoé"), 301, CheckoutRule.SINGLE_OUT),
        MatchFormat(legsPerSet = 3, sets = 2),
        listOf(ColorSlot.SLOT_2, ColorSlot.SLOT_4, ColorSlot.SLOT_1),
    )

    fun bytes(name: String): ByteArray =
        checkNotNull(V3Fixtures::class.java.getResourceAsStream("/persistence/v3/$name")) { name }.readBytes()
}
