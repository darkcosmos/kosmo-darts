package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.testing.savedGame
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor.Companion.BLUE
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor.Companion.ORANGE
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor.Companion.TEAL
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor.Companion.LILAC
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures.match
import io.github.darkcosmos.kosmodarts.ui.app.GameMode
import io.github.darkcosmos.kosmodarts.ui.app.cricketRematchSetup
import io.github.darkcosmos.kosmodarts.ui.app.rematchSetup
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The ARGB `colors` line of format version 2 in both files (docs/adr/0013-save-format-v2-argb-colors.md; rules R9,
 * R10, R11, R17, R30, R31; PC1, PC7, PC8; decision D15 of the plan of #51).
 */
class SaveColorsCodecTest {

    private val gameCodec = GameSaveCodec()
    private val setupCodec = LastSetupCodec()

    /** A generated color, outside the palette (PC15). */
    private val yellow = PlayerColor(0xFFF2D94E.toInt())

    /** Three players holding a generated color and two palette colors, in the middle of the second leg of a match. */
    private val saved = savedGame(
        match("Ann", "Bob", "Cid", legsPerSet = 2, colors = listOf(yellow, BLUE, LILAC))
            .confirmLegsWonBy(1)
            .throwing(Dart.triple(20)),
        isAwaitingNextPlayer = false,
    )

    private val gameBody: List<String> = coveredLines(checkNotNull(gameCodec.encode(saved)))

    private val setup =
        X01MatchConfig(X01Config(listOf("Ann", "Bob", "Cid")), colors = listOf(ORANGE, yellow, BLUE))

    private val setupBody: List<String> = coveredLines(setupCodec.encode(setup.toLastSetups()))

    private fun assertGameInvalid(expected: InvalidSaveReason, lines: List<String>) {
        assertEquals(lines.toString(), GameDecodeResult.Invalid(expected), gameCodec.decode(withChecksum(lines)))
    }

    private fun List<String>.withColors(value: String): List<String> =
        map { if (it.startsWith("colors=")) "colors=$value" else it }

    private fun List<String>.withVersion(version: Int): List<String> =
        map { if (it.startsWith("version=")) "version=$version" else it }

    /** R17, R31: a saved X01 game keeps a generated color and non-positional colors, written as ARGB. */
    @Test
    fun `R31 a saved X01 game keeps generated colors as ARGB`() {
        val decoded = gameCodec.decode(checkNotNull(gameCodec.encode(saved)))

        assertEquals(GameDecodeResult.Valid(saved), decoded)
        assertEquals(listOf(yellow, BLUE, LILAC), (decoded as GameDecodeResult.Valid).x01.match.colors)
        assertEquals("version=2", gameBody[1])
        assertEquals("colors=FFF2D94E FF1F63D6 FFC9A7FF", gameBody.single { it.startsWith("colors=") })
    }

    /** R17, R31: a saved Cricket game keeps a generated color. */
    @Test
    fun `R31 a saved Cricket game keeps generated colors`() {
        val cricket = CricketSaveFixtures.saved(CricketSaveFixtures.match("Ann", "Bob", colors = listOf(TEAL, yellow)))

        val decoded = gameCodec.decode(checkNotNull(gameCodec.encode(cricket)))

        assertEquals(GameDecodeResult.Valid(cricket), decoded)
        assertEquals(listOf(TEAL, yellow), (decoded as GameDecodeResult.Valid).cricket.match.colors)
    }

    /** R17, R31: the last setup keeps a generated color and non-positional colors. */
    @Test
    fun `R31 a last setup keeps generated colors`() {
        val decoded = setupCodec.decode(setupCodec.encode(setup.toLastSetups()))

        assertEquals(setup, decoded?.x01Setup)
        assertEquals(listOf(ORANGE, yellow, BLUE), decoded?.roster?.colors)
        assertEquals("version=2", setupBody[1])
        assertEquals("colors=FFE07020 FFF2D94E FF1F63D6", setupBody.single { it.startsWith("colors=") })
    }

    /** PC7: a rematch after a resume keeps each player's color, the first player moving to the end with his. */
    @Test
    fun `PC7 a rematch after a resume keeps the colors`() {
        val x01 = (gameCodec.decode(checkNotNull(gameCodec.encode(saved))) as GameDecodeResult.Valid).x01
        val cricket = CricketSaveFixtures.saved(CricketSaveFixtures.match("Ann", "Bob", colors = listOf(TEAL, yellow)))
        val resumed = (gameCodec.decode(checkNotNull(gameCodec.encode(cricket))) as GameDecodeResult.Valid).cricket

        assertEquals(listOf(BLUE, LILAC, yellow), rematchSetup(x01.match).colors)
        assertEquals(listOf(yellow, TEAL), cricketRematchSetup(resumed.match).colors)
    }

    /** PC8: the setup prefilled from the last setup file restores each player's color, in both modes. */
    @Test
    fun `PC8 the setup from the last setup restores the colors`() {
        val decoded = setupCodec.decode(setupCodec.encode(setup.toLastSetups()))

        assertEquals(listOf(ORANGE, yellow, BLUE), SetupState.prefilled(decoded, GameMode.X01).roster.colors)
        assertEquals(listOf(ORANGE, yellow, BLUE), SetupState.prefilled(decoded, GameMode.CRICKET).roster.colors)
    }

    /**
     * D15, R31: on load only format, opacity and uniqueness are checked, not PC11 to PC13: opaque, distinct colors that
     * are unreadable on the background (black, the background itself) are accepted and resume as they are.
     */
    @Test
    fun `D15 opaque distinct colors that fail PC11 are accepted`() {
        val unreadable = "FF000000 FF0A0F1E FF1F63D6"
        val loaded = gameCodec.decode(withChecksum(gameBody.withColors(unreadable)))
        val setupLoaded = setupCodec.decode(withChecksum(setupBody.withColors(unreadable)))
        val dark = listOf(PlayerColor(0xFF000000.toInt()), PlayerColor(0xFF0A0F1E.toInt()), BLUE)

        assertEquals(dark, (loaded as GameDecodeResult.Valid).x01.match.colors)
        assertEquals(dark, setupLoaded?.roster?.colors)
    }

    /** R9, R31: one color per player, separated by single spaces; another count or an empty token is malformed. */
    @Test
    fun `R9 a colors line with the wrong count is malformed`() {
        listOf(
            "FFF2D94E FF1F63D6",
            "FFF2D94E FF1F63D6 FFC9A7FF FFE07020",
            "FFF2D94E  FF1F63D6 FFC9A7FF",
            "FFF2D94E FF1F63D6 FFC9A7FF ",
            "",
        ).forEach { assertGameInvalid(InvalidSaveReason.MALFORMED, gameBody.withColors(it)) }
    }

    /** R9, R31: a token that is not 8 uppercase hex digits with alpha `FF` is out of range, never a crash (R11). */
    @Test
    fun `R9 a color that is not 8 uppercase hex digits with alpha FF is out of range`() {
        listOf(
            "FFF2D94E FF1F63D6 C9A7FF", // 6 digits
            "FFF2D94E FF1F63D6 0FFC9A7FF", // 9 digits
            "FFF2D94E FF1F63D6 ffc9a7ff", // lowercase
            "FFF2D94E FF1F63D6 FFc9A7FF", // one lowercase digit
            "FFF2D94E FF1F63D6 80C9A7FF", // alpha not FF
            "FFF2D94E FF1F63D6 00C9A7FF", // transparent
            "FFF2D94E FF1F63D6 FFC9A7FG", // not hex
            "FFF2D94E FF1F63D6 +FC9A7FF", // sign
            "FFF2D94E FF1F63D6 0xC9A7FF", // prefix
            "3 1 4", // version 1 slot numbers
        ).forEach { assertGameInvalid(InvalidSaveReason.OUT_OF_RANGE, gameBody.withColors(it)) }
    }

    /** R9, R31, PC1: two players with the same color are out of range. */
    @Test
    fun `R9 a repeated color is out of range`() {
        assertGameInvalid(InvalidSaveReason.OUT_OF_RANGE, gameBody.withColors("FFF2D94E FF1F63D6 FFF2D94E"))
    }

    /** R30, R31: a version 1 file (color slots) is not read: the saved game is rejected (R9). */
    @Test
    fun `R30 a version 1 saved game is unsupported`() {
        assertGameInvalid(InvalidSaveReason.UNSUPPORTED_VERSION, gameBody.withVersion(1).withColors("3 1 4"))
        assertGameInvalid(InvalidSaveReason.UNSUPPORTED_VERSION, gameBody.withVersion(1))
    }

    /** R31: the `colors` value is each color as 8 uppercase hex digits, separated by single spaces. */
    @Test
    fun `R31 colors are written as 8 uppercase hex digits`() {
        assertEquals("FFC9A7FF FF0C7C6A FF000000", colorsValue(listOf(LILAC, TEAL, PlayerColor(0xFF000000.toInt()))))
    }

    /** S1, S2, R9: a file without its colors line is malformed. */
    @Test
    fun `R9 a missing colors line is malformed`() {
        assertGameInvalid(InvalidSaveReason.MALFORMED, gameBody.filterNot { it.startsWith("colors=") })
    }

    /** S1, S2, R9: a duplicated colors key is malformed. */
    @Test
    fun `R9 a duplicated colors key is malformed`() {
        val doubled = gameBody.flatMap { if (it.startsWith("colors=")) listOf(it, it) else listOf(it) }

        assertGameInvalid(InvalidSaveReason.MALFORMED, doubled)
    }

    /** S1, S2, R9: a colors line placed before the player lines is malformed. */
    @Test
    fun `R9 a colors line before the player lines is malformed`() {
        val colors = gameBody.single { it.startsWith("colors=") }
        val withoutColors = gameBody - colors
        val firstPlayer = withoutColors.indexOfFirst { it.startsWith("player=") }
        val moved = withoutColors.take(firstPlayer) + colors + withoutColors.drop(firstPlayer)

        assertGameInvalid(InvalidSaveReason.MALFORMED, moved)
    }

    /** S2, R9: an extra line after the colors line (a second colors value) is malformed. */
    @Test
    fun `R9 an extra colors line is malformed`() {
        val extra = gameBody.flatMap {
            if (it.startsWith("colors=")) listOf(it, "colors=FF1F63D6 FFE07020 FF0C7C6A") else listOf(it)
        }

        assertGameInvalid(InvalidSaveReason.MALFORMED, extra)
    }

    /** R10, R30, R31: the same invalid colors lines, and a version 1 file, make the last setup unreadable. */
    @Test
    fun `R10 an invalid colors line or a version 1 file makes the last setup unreadable`() {
        val colors = setupBody.single { it.startsWith("colors=") }
        listOf(
            setupBody.withColors("FFE07020 FFF2D94E"),
            setupBody.withColors("FFE07020 FFF2D94E FF1F63D6 FFC9A7FF"),
            setupBody.withColors("FFE07020 FFF2D94E ff1f63d6"),
            setupBody.withColors("FFE07020 FFF2D94E 001F63D6"),
            setupBody.withColors("FFE07020 FFF2D94E 1F63D6"),
            setupBody.withColors("FFE07020 FFF2D94E FFE07020"),
            setupBody.withColors("2 4 1"),
            setupBody - colors,
            setupBody + colors,
            setupBody.withVersion(1),
            setupBody.withVersion(1).withColors("2 4 1"),
        ).forEach { assertNull(it.toString(), setupCodec.decode(withChecksum(it))) }
    }
}
