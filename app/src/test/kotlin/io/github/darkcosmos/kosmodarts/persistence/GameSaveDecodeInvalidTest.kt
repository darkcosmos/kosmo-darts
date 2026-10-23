package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.zip.CRC32

/** R9: every way a saved game file can be unreadable is rejected, with the first broken check as the reason. */
class GameSaveDecodeInvalidTest {

    private val codec = GameSaveCodec()

    private val valid: String = String(checkNotNull(codec.encode(SaveFixtures.WAITING_NORMAL)), Charsets.UTF_8)

    /** The covered part (kind to last body line) of the valid file, as lines. */
    private val validBody: List<String> = valid.lines().dropLast(TRAILER_LINES)

    private fun assertInvalid(expected: InvalidSaveReason, bytes: ByteArray) {
        assertEquals(GameDecodeResult.Invalid(expected), codec.decode(bytes))
    }

    private fun assertInvalid(expected: InvalidSaveReason, text: String) =
        assertInvalid(expected, text.toByteArray(Charsets.UTF_8))

    /** A file with these covered lines and a correct checksum, so that only the line content is wrong. */
    private fun withChecksum(lines: List<String>): String {
        val covered = lines.joinToString(separator = "\n", postfix = "\n")
        val crc = CRC32().apply { update(covered.toByteArray(Charsets.UTF_8)) }.value
        return covered + "crc32=" + crc.toString(16).padStart(8, '0') + "\nend\n"
    }

    /** Replaces the value of [key] in the valid file and fixes the checksum. */
    private fun withValue(key: String, value: String): String =
        withChecksum(validBody.map { if (it.startsWith("$key=")) "$key=$value" else it })

    private fun base64(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    @Test
    fun `R9 the valid fixture decodes`() {
        assertEquals(GameDecodeResult.Valid(SaveFixtures.WAITING_NORMAL), codec.decode(valid.toByteArray()))
        assertEquals(valid, withChecksum(validBody))
    }

    @Test
    fun `R9 an empty file is malformed`() = assertInvalid(InvalidSaveReason.MALFORMED, ByteArray(0))

    /** R9: a file cut anywhere is rejected; the checksum or the trailer catches every cut. */
    @Test
    fun `R9 every truncation is rejected`() {
        val bytes = valid.toByteArray(Charsets.UTF_8)
        for (length in 0 until bytes.size) {
            val result = codec.decode(bytes.copyOf(length))
            assertTrue("prefix of $length bytes was accepted", result is GameDecodeResult.Invalid)
        }
    }

    @Test
    fun `R9 a file over the size limit is rejected before parsing`() {
        assertInvalid(InvalidSaveReason.TOO_LARGE, ByteArray(GameSaveCodec.MAX_FILE_BYTES + 1) { 'a'.code.toByte() })
    }

    @Test
    fun `R9 another kind of file is malformed`() {
        assertInvalid(InvalidSaveReason.MALFORMED, valid.replaceFirst("kosmodarts=saved-game", "kosmodarts=last-setup"))
    }

    /** R9, R16, R18, R23: any version but 1 to 4 is rejected, even when the rest is a valid version 4 file. */
    @Test
    fun `R9 unknown, older and newer versions are unsupported`() {
        listOf("0", "5", "99", "01", "04", "x", "").forEach { version ->
            assertInvalid(InvalidSaveReason.UNSUPPORTED_VERSION, withValue("version", version))
        }
    }

    /** R23: a version 4 file without its mode line is malformed. */
    @Test
    fun `R23 a version 4 file without a mode line is malformed`() {
        assertInvalid(InvalidSaveReason.MALFORMED, withChecksum(validBody.filterNot { it == "mode=x01" }))
    }

    @Test
    fun `R23 an unknown mode value is out of range`() {
        listOf("cricket2", "X01", "around-the-clock", "").forEach {
            assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withValue("mode", it))
        }
    }

    /** R23: the body must match its mode line. */
    @Test
    fun `R23 a body that does not match its mode is malformed`() {
        assertInvalid(InvalidSaveReason.MALFORMED, withValue("mode", "cricket"))
        val cricket = coveredLines(checkNotNull(codec.encode(CricketSaveFixtures.MID_TURN)))
        val x01Mode = cricket.map { if (it == "mode=cricket") "mode=x01" else it }
        assertInvalid(InvalidSaveReason.MALFORMED, withChecksum(x01Mode))
    }

    /** R23, R25: versions 1 to 3 have no mode line; one inside them is malformed. */
    @Test
    fun `R23 a mode line in a version 1, 2 or 3 file is malformed`() {
        listOf("1", "2", "3").forEach { version ->
            val old = validBody.map { if (it == "version=4") "version=$version" else it }
            assertInvalid(InvalidSaveReason.MALFORMED, withChecksum(old))
        }
        val v1 = coveredLines(V1Fixtures.bytes("game-golden.save"))
        val v1WithMode = v1.take(2) + "mode=x01" + v1.drop(2)
        assertInvalid(InvalidSaveReason.MALFORMED, withChecksum(v1WithMode))
    }

    /** R23: the checksum covers the mode line. */
    @Test
    fun `R23 a checksum that does not cover the mode line is a mismatch`() {
        val withoutMode = withChecksum(validBody.filterNot { it == "mode=x01" })
        val crcWithoutMode = withoutMode.lines()[withoutMode.lines().size - TRAILER_LINES]
        val crcLine = valid.lines()[valid.lines().size - TRAILER_LINES]
        assertInvalid(InvalidSaveReason.CHECKSUM_MISMATCH, valid.replace(crcLine, crcWithoutMode))
        assertInvalid(InvalidSaveReason.CHECKSUM_MISMATCH, valid.replace("mode=x01", "mode=cricket"))
    }

    @Test
    fun `R9 structural damage is malformed`() {
        val damaged = listOf(
            "\uFEFF" + valid, // Byte order mark.
            valid.replace("\n", "\r\n"), // CRLF.
            withValue("darts", "T20 T20\r20"), // CR inside a value.
            valid.dropLast(1), // No final line feed.
            withChecksum(validBody + ""), // Empty line.
            withChecksum(validBody + validBody.last()), // Duplicate key.
            withChecksum(validBody.take(3) + validBody[4] + validBody[3] + validBody.drop(5)), // Keys out of order.
            withChecksum(validBody + "colour=red"), // Unknown key.
            valid.removeSuffix("end\n"), // Missing end.
            valid + "more\n", // Data after end.
        )
        damaged.forEach { assertInvalid(InvalidSaveReason.MALFORMED, it) }
    }

    @Test
    fun `R9 a changed byte breaks the checksum`() {
        assertInvalid(InvalidSaveReason.CHECKSUM_MISMATCH, valid.replace("darts=T20 T20 20", "darts=T20 T20 19"))
    }

    @Test
    fun `R9 a malformed checksum line is malformed`() {
        val crc = valid.lines()[valid.lines().size - TRAILER_LINES]
        assertInvalid(InvalidSaveReason.MALFORMED, valid.replace(crc, "crc32=ABCDEF12"))
        assertInvalid(InvalidSaveReason.MALFORMED, valid.replace(crc, crc.dropLast(1)))
    }

    @Test
    fun `R9 out of range settings are rejected`() {
        listOf(
            "game" to "cricket",
            "start-score" to "401",
            "start-score" to "0501",
            "start-score" to "+501",
            "start-score" to "501000",
            "checkout" to "double_out",
            "players" to "0",
            "players" to "5",
        ).forEach { (key, value) -> assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withValue(key, value)) }
    }

    @Test
    fun `R9 a player count that does not match the player lines is malformed`() {
        assertInvalid(InvalidSaveReason.MALFORMED, withValue("players", "3"))
        assertInvalid(InvalidSaveReason.MALFORMED, withValue("players", "1"))
    }

    @Test
    fun `R9 invalid names are rejected`() {
        val bob = base64("Bob")
        val outOfRange = listOf(
            "Qm9i!", // Not Base64.
            "Qm9", // Missing padding.
            "Qm9=", // Extra bits: decodes to "Bo", but is not the canonical form of it.
            Base64.getEncoder().encodeToString(byteArrayOf(0xC3.toByte(), 0x28)), // Invalid UTF-8.
            base64(" Bob"), // Not trimmed.
            base64("Cle\u0301ment"), // Not NFC (decomposed).
            "A".repeat(109), // Longer than any accepted name.
        )
        outOfRange.forEach { assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withValue("player", it)) }
        // The engine rules (C4): blank, too long, duplicate ignoring case.
        listOf(base64(""), base64("É".repeat(21))).forEach {
            assertInvalid(InvalidSaveReason.INVALID_CONFIG, withValue("player", it))
        }
        val duplicate = validBody.map { if (it == "player=$bob") "player=${base64("ANN")}" else it }
        assertInvalid(InvalidSaveReason.INVALID_CONFIG, withChecksum(duplicate))
    }

    @Test
    fun `R9 invalid dart tokens are rejected`() {
        listOf("S5", "21", "D25", "T50", "D0", "05", "T20 T20 20 ").forEach {
            val expected = if (it.endsWith(" ")) InvalidSaveReason.MALFORMED else InvalidSaveReason.OUT_OF_RANGE
            assertInvalid(expected, withValue("darts", it))
        }
        assertInvalid(InvalidSaveReason.MALFORMED, withValue("darts", "T20  20"))
    }

    @Test
    fun `R9 more than 10,000 darts are out of range`() {
        val tooMany = List(GameSaveCodec.MAX_DARTS + 1) { "0" }.joinToString(" ")

        assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withValue("darts", tooMany))
    }

    /** R9: the engine rejects a dart after the win (rule S5). */
    @Test
    fun `R9 a dart after the win is rejected by the engine`() {
        val checkout = String(checkNotNull(codec.encode(SaveFixtures.WAITING_CHECKOUT)), Charsets.UTF_8)
        val body = checkout.lines().dropLast(TRAILER_LINES)
            .map { if (it.startsWith("darts=")) "$it 0" else it }

        assertInvalid(InvalidSaveReason.REJECTED_BY_ENGINE, withChecksum(body))
    }

    /** R9 and ADR 0001: the waiting flag must agree with the replayed game. */
    @Test
    fun `R9 an inconsistent waiting flag is rejected`() {
        val midTurn = SavedGame(SaveFixtures.MID_TURN.game, isAwaitingNextPlayer = true)
        val noDart = SavedGame(X01Game(SaveFixtures.config()), isAwaitingNextPlayer = true)
        val finishedNotWaiting = SaveFixtures.WAITING_CHECKOUT.copy(isAwaitingNextPlayer = false)
        listOf(midTurn, noDart, finishedNotWaiting).forEach {
            // The encoder writes what it is given; the decoder must refuse it.
            assertInvalid(InvalidSaveReason.INCONSISTENT_WAITING_FLAG, checkNotNull(codec.encode(it)))
        }
    }

    @Test
    fun `R9 a non boolean waiting flag is out of range`() {
        assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withValue("awaiting-next-player", "yes"))
    }

    @Test
    fun `R9 an unknown dart after a valid one is not partly accepted`() {
        val game = X01Game(X01Config(listOf("Ann", "Bob")), listOf(Dart.triple(20)))
        val text = String(checkNotNull(codec.encode(SavedGame(game, false))), Charsets.UTF_8)
        val body = text.lines().dropLast(TRAILER_LINES).map { if (it.startsWith("darts=")) "darts=T20 X" else it }

        assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withChecksum(body))
    }

    private companion object {
        /** CRC line, end line and the empty string after the final line feed. */
        const val TRAILER_LINES = 3
    }
}
