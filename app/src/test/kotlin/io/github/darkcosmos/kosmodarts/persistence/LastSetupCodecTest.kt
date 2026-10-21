package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.zip.CRC32

/**
 * The last setup file (docs/adr/0001-save-format.md, docs/adr/0005-save-format-v2-match.md; rules R7, R10, R13,
 * R16).
 */
class LastSetupCodecTest {

    private val codec = LastSetupCodec()

    private val valid = String(codec.encode(X01MatchConfig(X01Config(listOf("Ann", "Bob")))), Charsets.UTF_8)

    /** The covered lines (kind to last body line) of the valid file. */
    private val validBody = valid.lines().dropLast(TRAILER_LINES)

    @Test
    fun `R7 R13 every setup round trips`() {
        for (players in 1..X01Config.MAX_PLAYERS) {
            for (startScore in X01Config.START_SCORES) {
                for (rule in CheckoutRule.entries) {
                    val setup = SaveFixtures.setup(players, startScore, rule)
                    assertEquals(setup, codec.decode(codec.encode(setup)))
                }
            }
        }
    }

    /** R13: the match format is saved with the last setup, bounds included. */
    @Test
    fun `R13 every match format bound round trips`() {
        listOf(MatchFormat.MIN, 3, MatchFormat.MAX).forEach { legs ->
            listOf(MatchFormat.MIN, 2, MatchFormat.MAX).forEach { sets ->
                val setup = SaveFixtures.setup(format = MatchFormat(legs, sets))
                assertEquals(setup, codec.decode(codec.encode(setup)))
            }
        }
    }

    @Test
    fun `R7 the order of the names is kept`() {
        val setup = X01MatchConfig(X01Config(listOf("Dan", "Ann", "Cid")))

        assertEquals(listOf("Dan", "Ann", "Cid"), codec.decode(codec.encode(setup))?.config?.players)
    }

    /** ADR 0005: version 2 inserts the format after the checkout rule; nothing else changes. */
    @Test
    fun `R13 version 2 layout`() {
        val setup = SaveFixtures.setup(format = MatchFormat(3, 2))
        val lines = String(codec.encode(setup), Charsets.UTF_8).lines()

        assertEquals(
            listOf(
                "kosmodarts=last-setup",
                "version=2",
                "game=x01",
                "start-score=501",
                "checkout=double-out",
                "legs-per-set=3",
                "sets=2",
                "players=2",
                "player=QW5u",
                "player=Qm9i",
            ),
            lines.dropLast(TRAILER_LINES),
        )
    }

    /** R16: the version 1 file written by the previous app is read as a single game, with its names in order. */
    @Test
    fun `R16 a version 1 last setup is a single game`() {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/persistence/v1/last-setup.save")).readBytes()

        assertEquals(
            X01MatchConfig(X01Config(listOf("Cid", "Ann", "Zoé"), 301, CheckoutRule.SINGLE_OUT), MatchFormat()),
            codec.decode(bytes),
        )
    }

    @Test
    fun `R10 invalid content decodes to nothing`() {
        listOf(
            "",
            valid.dropLast(1),
            valid.replace("version=2", "version=3"),
            valid.replace("start-score=501", "start-score=401"),
            valid.replace("kosmodarts=last-setup", "kosmodarts=saved-game"),
            valid.replace("players=2", "players=3"),
            valid.replace("\n", "\r\n"),
        ).forEach { assertNull(it, codec.decode(it.toByteArray(Charsets.UTF_8))) }
    }

    /** R10, M1: a format outside 1 to 9 is invalid, even with a correct checksum. */
    @Test
    fun `R10 an out of range format decodes to nothing`() {
        listOf("legs-per-set=0", "legs-per-set=10", "sets=0", "sets=10", "sets=01").forEach { line ->
            val key = line.substringBefore('=')
            val body = validBody.map { if (it.startsWith("$key=")) line else it }
            assertNull(line, codec.decode(withChecksum(body)))
        }
    }

    /** S2: strict keys per version. Version 1 with a version 2 key, version 2 without one, reordered or doubled. */
    @Test
    fun `R10 keys must follow the layout of their version`() {
        val v1Body = validBody.filterNot { it.startsWith("legs-per-set=") || it.startsWith("sets=") }
            .map { if (it == "version=2") "version=1" else it }
        assertEquals(SaveFixtures.setup(), codec.decode(withChecksum(v1Body)))
        val legsLine = validBody.first { it.startsWith("legs-per-set=") }
        val setsLine = validBody.first { it.startsWith("sets=") }
        listOf(
            v1Body.take(5) + legsLine + setsLine + v1Body.drop(5), // Version 1 with the version 2 keys.
            validBody - setsLine, // Missing key.
            validBody.flatMap { if (it == setsLine) listOf(it, it) else listOf(it) }, // Duplicated key.
            validBody.map { if (it == legsLine) setsLine else if (it == setsLine) legsLine else it }, // Out of order.
        ).forEach { body -> assertNull(body.toString(), codec.decode(withChecksum(body))) }
    }

    @Test
    fun `R10 a duplicate name is an invalid setup`() {
        val bob = SaveFormat.encodeName("Bob")
        val duplicate = valid.replace("player=$bob", "player=${SaveFormat.encodeName("ann")}")

        // The checksum no longer matches either: whatever the first broken check, nothing is accepted.
        assertNull(codec.decode(duplicate.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `R10 a file over the size limit is rejected`() {
        assertNull(codec.decode(ByteArray(LastSetupCodec.MAX_FILE_BYTES + 1)))
    }

    private fun withChecksum(lines: List<String>): ByteArray {
        val covered = lines.joinToString(separator = "\n", postfix = "\n")
        val crc = CRC32().apply { update(covered.toByteArray(Charsets.UTF_8)) }.value
        return (covered + "crc32=" + crc.toString(16).padStart(8, '0') + "\nend\n").toByteArray(Charsets.UTF_8)
    }

    private companion object {
        /** CRC line, end line and the empty string after the final line feed. */
        const val TRAILER_LINES = 3
    }
}
