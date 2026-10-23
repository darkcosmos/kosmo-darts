package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatchConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.zip.CRC32

/**
 * The last setup file (docs/adr/0001-save-format.md, docs/adr/0005-save-format-v2-match.md,
 * docs/adr/0006-save-format-v3-colors.md, docs/adr/0008-save-format-v4-modes.md; rules R7, R10, R13, R16 to R18,
 * R22, R23, R25).
 */
class LastSetupCodecTest {

    private val codec = LastSetupCodec()

    private val valid = encoded(X01MatchConfig(X01Config(listOf("Ann", "Bob"))).toLastSetups())

    /** The covered lines (kind to last body line) of the valid file. */
    private val validBody = valid.lines().dropLast(TRAILER_LINES)

    /** The covered lines of a valid version 4 file with both mode sections (R22). */
    private val bothModesBody = encoded(BOTH_MODES).lines().dropLast(TRAILER_LINES)

    @Test
    fun `R7 R13 every setup round trips`() {
        for (players in 1..X01Config.MAX_PLAYERS) {
            for (startScore in X01Config.START_SCORES) {
                for (rule in CheckoutRule.entries) {
                    val setups = SaveFixtures.setup(players, startScore, rule).toLastSetups()
                    assertEquals(setups, codec.decode(codec.encode(setups)))
                }
            }
        }
    }

    /** R13: the match format is saved with the last setup, bounds included. */
    @Test
    fun `R13 every match format bound round trips`() {
        listOf(MatchFormat.MIN, 3, MatchFormat.MAX).forEach { legs ->
            listOf(MatchFormat.MIN, 2, MatchFormat.MAX).forEach { sets ->
                val setups = SaveFixtures.setup(format = MatchFormat(legs, sets)).toLastSetups()
                assertEquals(setups, codec.decode(codec.encode(setups)))
            }
        }
    }

    @Test
    fun `R7 the order of the names is kept`() {
        val setups = X01MatchConfig(X01Config(listOf("Dan", "Ann", "Cid"))).toLastSetups()

        assertEquals(listOf("Dan", "Ann", "Cid"), codec.decode(codec.encode(setups))?.roster?.players)
    }

    /** R22: every Cricket variant, 25-Bull option and format bound round trips, alone and with X01 settings. */
    @Test
    fun `R22 every Cricket setting round trips with and without X01 settings`() {
        for (variant in CricketVariant.entries) {
            for (isBullInPlay in listOf(true, false)) {
                for (format in listOf(MatchFormat(), MatchFormat(MatchFormat.MAX, MatchFormat.MAX))) {
                    val cricket = CricketSettings(variant, isBullInPlay, format)
                    val alone = BOTH_MODES.copy(x01 = null, cricket = cricket)
                    val both = BOTH_MODES.copy(cricket = cricket)
                    assertEquals(alone, codec.decode(codec.encode(alone)))
                    assertEquals(both, codec.decode(codec.encode(both)))
                }
            }
        }
    }

    /**
     * ADR 0008, R22, R23: version 4 has one section per mode, each opened by its mode line (X01 first), then the shared
     * roster. Frozen: any change needs a version 5.
     */
    @Test
    fun `R22 R23 version 4 layout with both modes`() {
        assertEquals(V4_BOTH_LINES, bothModesBody)
        assertEquals(BOTH_MODES, codec.decode(withChecksum(V4_BOTH_LINES)))
    }

    /** R22: a file written after only Cricket games has no X01 section; X01 then uses its defaults with the roster. */
    @Test
    fun `R22 a Cricket only setup has no X01 section and gives the X01 defaults`() {
        val cricketOnly = V4_BOTH_LINES.take(2) + V4_BOTH_LINES.drop(CRICKET_SECTION_START)

        val decoded = codec.decode(withChecksum(cricketOnly))

        assertEquals(BOTH_MODES.copy(x01 = null), decoded)
        assertEquals(X01MatchConfig(X01Config(listOf("Ann", "Bob")), colorSlots = SLOTS_4_2), decoded?.x01Setup)
        assertEquals(cricketOnly, encoded(BOTH_MODES.copy(x01 = null)).lines().dropLast(TRAILER_LINES))
    }

    /** R22: a Start in one mode writes the roster and that mode's settings, and keeps the other mode's settings. */
    @Test
    fun `R22 a start keeps the settings of the other mode`() {
        val x01 = X01MatchConfig(X01Config(listOf("Cid"), 301, CheckoutRule.SINGLE_OUT), MatchFormat(2, 1))
        val afterX01 = LastSetups.afterX01(BOTH_MODES, x01)

        assertEquals(BOTH_MODES.cricket, afterX01.cricket)
        assertEquals(x01, afterX01.x01Setup)
        val cricket = CricketMatchConfig(CricketConfig(listOf("Dan", "Eve"), CricketVariant.STANDARD, true))
        val afterCricket = LastSetups.afterCricket(afterX01, cricket)
        assertEquals(afterX01.x01, afterCricket.x01)
        assertEquals(cricket, afterCricket.cricketSetup)
        assertEquals(afterCricket, codec.decode(codec.encode(afterCricket)))
    }

    /**
     * ADR 0006, R17: version 3 is the version 2 layout (format after the checkout rule) plus the color slots after
     * the player lines. Frozen, still read (R25).
     */
    @Test
    fun `R13 R17 R25 a version 3 last setup is read as X01`() {
        val decoded = codec.decode(withChecksum(V3_LINES))

        val expected = X01MatchConfig(X01Config(listOf("Ann", "Bob")), MatchFormat(3, 2), SLOTS_4_2)
        assertEquals(expected.toLastSetups(), decoded)
        assertEquals(expected, decoded?.x01Setup)
    }

    /** R25: a version 1 to 3 file has no Cricket settings, so Cricket uses its defaults with the shared roster. */
    @Test
    fun `R25 an older last setup gives the Cricket defaults with the roster`() {
        val decoded = codec.decode(withChecksum(V3_LINES))

        assertNull(decoded?.cricket)
        val expected = CricketMatchConfig(CricketConfig(listOf("Ann", "Bob")), colorSlots = SLOTS_4_2)
        assertEquals(expected, decoded?.cricketSetup)
    }

    /** ADR 0005, R18: the version 2 layout, frozen, is still read, with the color slots of the player positions. */
    @Test
    fun `R18 a version 2 last setup has slots by position`() {
        val decoded = codec.decode(withChecksum(V2_LINES))

        assertEquals(X01MatchConfig(X01Config(listOf("Ann", "Bob")), MatchFormat(3, 2)), decoded?.x01Setup)
        assertEquals(ColorSlot.byPosition(2), decoded?.roster?.colorSlots)
    }

    /** R16: the version 1 file written by the previous app is read as a single game, with its names in order. */
    @Test
    fun `R16 a version 1 last setup is a single game`() {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/persistence/v1/last-setup.save")).readBytes()

        assertEquals(
            X01MatchConfig(X01Config(listOf("Cid", "Ann", "Zoé"), 301, CheckoutRule.SINGLE_OUT), MatchFormat()),
            codec.decode(bytes)?.x01Setup,
        )
    }

    /**
     * R23 (review T2, S1): a missing or unknown mode line, a mode line in version 1 to 3, a repeated or out of order
     * section, and a section that does not match its mode are all rejected, even with a correct checksum.
     */
    @Test
    fun `R23 mode line rejections`() {
        val x01Section = V4_BOTH_LINES.subList(2, CRICKET_SECTION_START)
        val cricketSection = V4_BOTH_LINES.subList(CRICKET_SECTION_START, ROSTER_START)
        val roster = V4_BOTH_LINES.drop(ROSTER_START)
        val header = V4_BOTH_LINES.take(2)
        listOf(
            header + roster, // No mode section at all.
            header + x01Section.drop(1) + roster, // X01 section without its mode line.
            header + listOf("mode=darts") + x01Section.drop(1) + roster, // Unknown mode.
            header + listOf("mode=") + x01Section.drop(1) + roster, // Empty mode.
            header + cricketSection + x01Section + roster, // Sections out of order.
            header + x01Section + x01Section + roster, // X01 twice.
            header + cricketSection + cricketSection + roster, // Cricket twice.
            header + listOf("mode=cricket") + x01Section.drop(1) + roster, // Cricket mode with an X01 body.
            header + listOf("mode=x01") + cricketSection.drop(1) + roster, // X01 mode with a Cricket body.
            V3_LINES.take(2) + "mode=x01" + V3_LINES.drop(2), // Mode line in version 3.
            V2_LINES.take(2) + "mode=x01" + V2_LINES.drop(2), // Mode line in version 2.
            header + x01Section + roster + "mode=cricket", // Mode line after the roster.
        ).forEach { body -> assertNull(body.toString(), codec.decode(withChecksum(body))) }
    }

    /** R23: the checksum covers the mode line: one computed without it, or a mode line edited afterwards, fails. */
    @Test
    fun `R23 the checksum covers the mode lines`() {
        val withoutModeLines = bothModesBody.filterNot { it.startsWith("mode=") }
        val crcWithoutModeLines = String(withChecksum(withoutModeLines), Charsets.UTF_8).lines().takeLast(TRAILER_LINES)
        val forged = (bothModesBody + crcWithoutModeLines).joinToString("\n")
        val bothModes = encoded(BOTH_MODES)

        assertNull(codec.decode(forged.toByteArray(Charsets.UTF_8)))
        assertNull(codec.decode(bothModes.replace("mode=cricket", "mode=x01").toByteArray(Charsets.UTF_8)))
        assertNull(codec.decode(bothModes.replace("crc32=", "crc32=0").toByteArray(Charsets.UTF_8)))
    }

    /** R24 (review S3): the variant and 25-Bull tokens come from a closed set, even with a correct checksum. */
    @Test
    fun `R24 unknown variant and 25-Bull tokens are rejected`() {
        listOf(
            "variant=cut-throat" to "variant=Cut-throat",
            "variant=cut-throat" to "variant=cutthroat",
            "variant=cut-throat" to "variant=",
            "bull=0" to "bull=2",
            "bull=0" to "bull=true",
            "bull=0" to "bull=",
        ).forEach { (line, replacement) ->
            val body = bothModesBody.map { if (it == line) replacement else it }
            assertNull(replacement, codec.decode(withChecksum(body)))
        }
    }

    /** R24: names that break `PlayerListRules` are rejected in a Cricket only file (the exception never escapes). */
    @Test
    fun `R24 invalid names in a Cricket only setup are rejected`() {
        val cricketOnly = V4_BOTH_LINES.take(2) + V4_BOTH_LINES.drop(CRICKET_SECTION_START)
        val ann = "player=${SaveFormat.encodeName("Ann")}"
        val bob = "player=${SaveFormat.encodeName("Bob")}"
        listOf(
            cricketOnly.map { if (it == bob) "player=${SaveFormat.encodeName("ann")}" else it }, // Duplicate.
            cricketOnly.map { if (it == bob) "player=${SaveFormat.encodeName("x".repeat(21))}" else it }, // Too long.
            cricketOnly.map { if (it == ann) "player=" else it }, // Blank.
            cricketOnly.map { if (it == "players=2") "players=0" else it } - ann - bob - "colors=4 2" + "colors=",
        ).forEach { body -> assertNull(body.toString(), codec.decode(withChecksum(body))) }
    }

    @Test
    fun `R10 invalid content decodes to nothing`() {
        listOf(
            "",
            valid.dropLast(1),
            valid.replace("version=4", "version=5"),
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

    /**
     * S2: strict keys per version. Version 1 with a version 2 key, version 2 with the version 3 key, versions 3 and 4
     * without one, reordered or doubled.
     */
    @Test
    fun `R10 keys must follow the layout of their version`() {
        val colorsLine = "colors=4 2"
        val v1Body = V3_LINES
            .filterNot { it.startsWith("legs-per-set=") || it.startsWith("sets=") || it == colorsLine }
            .map { if (it == "version=3") "version=1" else it }
        assertEquals(SaveFixtures.setup().toLastSetups(), codec.decode(withChecksum(v1Body)))
        val legsLine = "legs-per-set=3"
        val setsLine = "sets=2"
        val v4X01Only = validBody
        val v4SetsLine = v4X01Only.first { it.startsWith("sets=") }
        val v4LegsLine = v4X01Only.first { it.startsWith("legs-per-set=") }
        listOf(
            v1Body.take(5) + legsLine + setsLine + v1Body.drop(5), // Version 1 with the version 2 keys.
            V2_LINES + colorsLine, // Version 2 with the version 3 key.
            V3_LINES - colorsLine, // Missing colors key.
            V3_LINES - setsLine, // Missing key.
            V3_LINES.flatMap { if (it == setsLine) listOf(it, it) else listOf(it) }, // Duplicated key.
            V3_LINES.map { if (it == legsLine) setsLine else if (it == setsLine) legsLine else it }, // Out of order.
            v4X01Only.filterNot { it.startsWith("colors=") }, // Version 4 without colors.
            v4X01Only - v4SetsLine, // Version 4 missing key.
            v4X01Only.map { if (it == v4LegsLine) v4SetsLine else if (it == v4SetsLine) v4LegsLine else it },
            bothModesBody.filterNot { it.startsWith("bull=") }, // Cricket section missing a key.
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

    private fun encoded(setups: LastSetups): String = String(codec.encode(setups), Charsets.UTF_8)

    private fun withChecksum(lines: List<String>): ByteArray {
        val covered = lines.joinToString(separator = "\n", postfix = "\n")
        val crc = CRC32().apply { update(covered.toByteArray(Charsets.UTF_8)) }.value
        return (covered + "crc32=" + crc.toString(16).padStart(8, '0') + "\nend\n").toByteArray(Charsets.UTF_8)
    }

    private companion object {
        /** CRC line, end line and the empty string after the final line feed. */
        const val TRAILER_LINES = 3

        /** The covered lines the version 2 encoder wrote for Ann and Bob, first to 3 legs and 2 sets (frozen, T2). */
        val V2_LINES = listOf(
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
        )

        /** The covered lines the version 3 encoder wrote for the same setup, with slots 4 and 2 (frozen, T6). */
        val V3_LINES = V2_LINES.map { if (it == "version=2") "version=3" else it } + "colors=4 2"

        val SLOTS_4_2 = listOf(ColorSlot.SLOT_4, ColorSlot.SLOT_2)

        /** A last setup after games in both modes (R22). */
        val BOTH_MODES = LastSetups(
            roster = SharedRoster(listOf("Ann", "Bob"), SLOTS_4_2),
            x01 = X01Settings(301, CheckoutRule.SINGLE_OUT, MatchFormat(3, 2)),
            cricket = CricketSettings(CricketVariant.CUT_THROAT, isBullInPlay = false, MatchFormat(2, 1)),
        )

        /** The covered lines of [BOTH_MODES] in version 4 (frozen, ADR 0008). */
        val V4_BOTH_LINES = listOf(
            "kosmodarts=last-setup",
            "version=4",
            "mode=x01",
            "game=x01",
            "start-score=301",
            "checkout=single-out",
            "legs-per-set=3",
            "sets=2",
            "mode=cricket",
            "variant=cut-throat",
            "bull=0",
            "legs-per-set=2",
            "sets=1",
            "players=2",
            "player=QW5u",
            "player=Qm9i",
            "colors=4 2",
        )

        /** Index of `mode=cricket` in [V4_BOTH_LINES]. */
        const val CRICKET_SECTION_START = 8

        /** Index of `players=2` in [V4_BOTH_LINES]. */
        const val ROSTER_START = 13
    }
}
