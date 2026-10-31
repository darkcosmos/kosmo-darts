package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketGame
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures.match
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures.saved
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Encoding, round trips and rejections of the Cricket saved game (docs/adr/0008-save-format-v4-modes.md; rules R9 to
 * R12, R21, R23, R24). The bounds of the confirmed legs are in [CricketSaveDecodeBoundsTest].
 */
class CricketSaveCodecTest {

    private val codec = GameSaveCodec()

    private fun assertRoundTrip(saved: SavedCricketGame) {
        val bytes = checkNotNull(codec.encode(saved)) { "$saved was not encoded" }

        assertEquals(GameDecodeResult.Valid(saved), codec.decode(bytes))
    }

    private fun assertInvalid(expected: InvalidSaveReason, bytes: ByteArray) {
        assertEquals(GameDecodeResult.Invalid(expected), codec.decode(bytes))
    }

    /** The covered lines of the file of [saved]. */
    private fun bodyOf(saved: SavedCricketGame): List<String> = coveredLines(checkNotNull(codec.encode(saved)))

    /** The file of [saved] with the value of [key] replaced and a correct checksum. */
    private fun withValue(saved: SavedCricketGame, key: String, value: String): ByteArray =
        withChecksum(bodyOf(saved).map { if (it.startsWith("$key=")) "$key=$value" else it })

    private fun base64(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    /** R21: every representative Cricket game comes back equal, with its two flags. */
    @Test
    fun `R21 representative Cricket games round trip`() {
        listOf(
            saved(match("Ann", "Bob")),
            CricketSaveFixtures.MID_TURN,
            CricketSaveFixtures.WAITING,
            CricketSaveFixtures.WAITING.copy(isAwaitingNextPlayer = false),
            CricketSaveFixtures.MATCH_MID_LEG,
            CricketSaveFixtures.BETWEEN_LEGS,
            CricketSaveFixtures.PENDING_MATCH_WIN,
            CricketSaveFixtures.NON_THROWER_WIN,
        ).forEach(::assertRoundTrip)
    }

    /** R21, CR25: a leg won by a player who is not the thrower keeps its winner. */
    @Test
    fun `R21 a win by a non-thrower is restored with its winner`() {
        val bytes = checkNotNull(codec.encode(CricketSaveFixtures.NON_THROWER_WIN))
        val restored = (codec.decode(bytes) as GameDecodeResult.Valid).cricket

        assertEquals(0, restored.game.state.winnerIndex)
        assertEquals(2, restored.game.state.completedTurns.last().playerIndex)
        assertTrue(restored.isAwaitingNextPlayer)
    }

    /** R21, CR1, CR6: 1 to 4 players, both variants, with and without the 25-Bull, played to a win or mid-leg. */
    @Test
    fun `R21 every player count, variant and 25-Bull option round trips`() {
        val names = listOf("Ann", "Bob", "Cid", "Dan")
        val darts = CricketSaveFixtures.darts("T20 T19 D18", "50 25 0", "T17 16")
        for (players in 1..names.size) {
            for (variant in CricketVariant.entries) {
                for (isBullInPlay in listOf(true, false)) {
                    val roster = names.take(players).toTypedArray()
                    val start = match(*roster, variant = variant, isBullInPlay = isBullInPlay)
                    assertRoundTrip(saved(start.throwing(*darts)))
                    assertRoundTrip(saved(start.playLegWonBy(players - 1)))
                }
            }
        }
    }

    /** R21, R17: the color slots of the roster are restored as chosen. */
    @Test
    fun `R21 non positional color slots round trip`() {
        val slots = listOf(ColorSlot.SLOT_4, ColorSlot.SLOT_2, ColorSlot.SLOT_1)
        val saved = saved(match("Ann", "Bob", "Cid", colorSlots = slots))
        val restored = (codec.decode(checkNotNull(codec.encode(saved))) as GameDecodeResult.Valid).cricket

        assertEquals(slots, restored.match.colorSlots)
    }

    /** R12: the largest current leg is saved; one more dart is not encoded, so the previous file stays. */
    @Test
    fun `R12 10,000 darts round trip and 10,001 are not encoded`() {
        assertRoundTrip(longestSolo)
        assertNull(codec.encode(saved(longestSolo.match.throwDart(Dart.MISS))))
    }

    /** One player and exactly [GameSaveCodec.MAX_DARTS] misses. */
    private val longestSolo: SavedCricketGame =
        saved(match("Ann").throwing(*Array(GameSaveCodec.MAX_DARTS) { Dart.MISS }))

    /** ADR 0008, R23: exact bytes of a Cricket match between legs, after the mode line. */
    @Test
    fun `format v4 golden file of a Cricket match`() {
        val golden = """
            kosmodarts=saved-game
            version=4
            mode=cricket
            variant=standard
            bull=1
            legs-per-set=3
            sets=2
            players=2
            player=QW5u
            player=Qm9i
            colors=1 2
            legs=2
            leg=0 21/8/0/21 0/6/0/0
            leg=1 0/6/0/0 21/8/0/21
            darts=
            awaiting-next-player=false
            between-legs=true
            crc32=d0694c71
            end

        """.trimIndent().toByteArray(Charsets.UTF_8)

        assertArrayEquals(golden, codec.encode(CricketSaveFixtures.BETWEEN_LEGS))
        assertEquals(GameDecodeResult.Valid(CricketSaveFixtures.BETWEEN_LEGS), codec.decode(golden))
    }

    /** ADR 0002, decision 3: a Cricket save is replayed once, in a single engine call, never dart by dart. */
    @Test
    fun `R12 decoding a Cricket save builds the current leg once`() {
        var builds = 0
        val counting = GameSaveCodec(cricketGameFactory = { config, darts, starter ->
            builds++
            CricketGame(config, darts, starter)
        })
        val bytes = checkNotNull(counting.encode(longestSolo))

        counting.decode(bytes)

        assertEquals(1, builds)
    }

    /** R9: a Cricket file cut anywhere is rejected. */
    @Test
    fun `R9 every truncation of a Cricket file is rejected`() {
        val bytes = checkNotNull(codec.encode(CricketSaveFixtures.MATCH_MID_LEG))
        for (length in 0 until bytes.size) {
            val result = codec.decode(bytes.copyOf(length))
            assertTrue("prefix of $length bytes was accepted", result is GameDecodeResult.Invalid)
        }
    }

    /** R9: the structural damage cases of the X01 file, on a Cricket body. */
    @Test
    fun `R9 structural damage of a Cricket body is malformed`() {
        val saved = CricketSaveFixtures.MATCH_MID_LEG
        val text = String(checkNotNull(codec.encode(saved)), Charsets.UTF_8)
        val body = bodyOf(saved)
        val variant = body.indexOfFirst { it.startsWith("variant=") }
        listOf(
            ("\uFEFF" + text).toByteArray(),
            text.replace("\n", "\r\n").toByteArray(),
            text.dropLast(1).toByteArray(),
            withChecksum(body + ""),
            withChecksum(body + body.last()),
            withChecksum(body.take(variant) + body[variant + 1] + body[variant] + body.drop(variant + 2)),
            withChecksum(body.filterNot { it.startsWith("bull=") }),
            withChecksum(body.filterNot { it.startsWith("variant=") }),
            withChecksum(body.filterNot { it.startsWith("colors=") }),
            withChecksum(body + "start-score=501"),
            text.removeSuffix("end\n").toByteArray(),
        ).forEach { assertInvalid(InvalidSaveReason.MALFORMED, it) }
    }

    @Test
    fun `R9 a changed byte of a Cricket file breaks the checksum`() {
        val text = String(checkNotNull(codec.encode(CricketSaveFixtures.MID_TURN)), Charsets.UTF_8)

        assertInvalid(InvalidSaveReason.CHECKSUM_MISMATCH, text.replace("darts=T20 19", "darts=T20 18").toByteArray())
    }

    /** R9, R24: out of range values of a Cricket body. */
    @Test
    fun `R9 out of range Cricket settings are rejected`() {
        val saved = CricketSaveFixtures.MID_TURN
        listOf(
            "legs-per-set" to "0",
            "legs-per-set" to "10",
            "sets" to "02",
            "colors" to "1 5",
            "colors" to "2 2",
            "awaiting-next-player" to "yes",
            "between-legs" to "1",
            "darts" to "T20 X",
            "darts" to "S5",
        ).forEach { (key, value) ->
            val result = codec.decode(withValue(saved, key, value))
            assertEquals("$key=$value", GameDecodeResult.Invalid(InvalidSaveReason.OUT_OF_RANGE), result)
        }
    }

    @Test
    fun `R9 a Cricket player count that does not match the player lines is malformed`() {
        assertInvalid(InvalidSaveReason.MALFORMED, withValue(CricketSaveFixtures.MID_TURN, "players", "3"))
        assertInvalid(InvalidSaveReason.MALFORMED, withValue(CricketSaveFixtures.MID_TURN, "players", "1"))
    }

    @Test
    fun `R9 invalid Cricket names are rejected`() {
        val game = CricketSaveFixtures.MID_TURN
        listOf("Qm9i!", "Qm9", base64(" Bob"), base64("Cle\u0301ment")).forEach {
            assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withValue(game, "player", it))
        }
        assertInvalid(InvalidSaveReason.INVALID_CONFIG, withValue(game, "player", base64("É".repeat(21))))
    }

    @Test
    fun `R9 more than 10,000 darts in a Cricket leg are out of range`() {
        val tooMany = List(GameSaveCodec.MAX_DARTS + 1) { "0" }.joinToString(" ")

        assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withValue(saved(match("Ann")), "darts", tooMany))
    }

    /** R9, CR9: the engine rejects a dart after the win, and the decoder reports it without throwing. */
    @Test
    fun `R9 a dart after the Cricket win is rejected by the engine`() {
        listOf(CricketSaveFixtures.PENDING_MATCH_WIN, CricketSaveFixtures.NON_THROWER_WIN).forEach { saved ->
            val body = bodyOf(saved).map { if (it.startsWith("darts=")) "$it 0" else it }

            assertInvalid(InvalidSaveReason.REJECTED_BY_ENGINE, withChecksum(body))
        }
    }

    /** R9, R21: the two flags must agree with the replayed Cricket match. */
    @Test
    fun `R9 inconsistent Cricket flags are rejected`() {
        listOf(
            CricketSaveFixtures.MID_TURN.copy(isAwaitingNextPlayer = true),
            saved(match("Ann", "Bob")).copy(isAwaitingNextPlayer = true),
            CricketSaveFixtures.PENDING_MATCH_WIN.copy(isAwaitingNextPlayer = false),
            CricketSaveFixtures.NON_THROWER_WIN.copy(isAwaitingNextPlayer = false),
            CricketSaveFixtures.MATCH_MID_LEG.copy(isBetweenLegs = true),
            saved(match("Ann", "Bob"), isBetweenLegs = true),
        ).forEach {
            // The encoder writes what it is given; the decoder must refuse it.
            assertInvalid(InvalidSaveReason.INCONSISTENT_WAITING_FLAG, checkNotNull(codec.encode(it)))
        }
    }

    /** R24, M6: the starter of the current leg follows the confirmed legs; it is not stored, so it cannot be wrong. */
    @Test
    fun `R24 the starter of the current leg comes from the confirmed legs`() {
        val bytes = checkNotNull(codec.encode(CricketSaveFixtures.MATCH_MID_LEG))
        val restored = (codec.decode(bytes) as GameDecodeResult.Valid).cricket

        assertEquals(1, restored.game.startingPlayerIndex)
    }
}
