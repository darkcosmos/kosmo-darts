package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot.SLOT_1
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot.SLOT_2
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot.SLOT_3
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot.SLOT_4
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures.match
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The color slots of format version 3 in both files (docs/adr/0006-save-format-v3-colors.md; rules R9, R10, R17, R18;
 * S1 of the design review of #49).
 */
class SaveColorsCodecTest {

    private val gameCodec = GameSaveCodec()
    private val setupCodec = LastSetupCodec()

    /** Three players holding slots 3, 1 and 4, in the middle of the second leg of a match. */
    private val saved = SavedGame(
        match("Ann", "Bob", "Cid", legsPerSet = 2, colorSlots = listOf(SLOT_3, SLOT_1, SLOT_4))
            .confirmLegsWonBy(1)
            .throwing(Dart.triple(20)),
        isAwaitingNextPlayer = false,
    )

    private val gameBody: List<String> = coveredLines(checkNotNull(gameCodec.encode(saved)))

    private val setup =
        X01MatchConfig(X01Config(listOf("Ann", "Bob", "Cid")), colorSlots = listOf(SLOT_2, SLOT_4, SLOT_1))

    private val setupBody: List<String> = coveredLines(setupCodec.encode(setup.toLastSetups()))

    private fun assertGameInvalid(expected: InvalidSaveReason, lines: List<String>) {
        assertEquals(lines.toString(), GameDecodeResult.Invalid(expected), gameCodec.decode(withChecksum(lines)))
    }

    private fun List<String>.withColors(value: String): List<String> =
        map { if (it.startsWith("colors=")) "colors=$value" else it }

    /** R17, PC6: a match with slots that are not those of the positions comes back with the same slots. */
    @Test
    fun `R17 a saved game keeps non-positional slots`() {
        val decoded = gameCodec.decode(checkNotNull(gameCodec.encode(saved)))

        assertEquals(GameDecodeResult.Valid(saved), decoded)
        assertEquals(listOf(SLOT_3, SLOT_1, SLOT_4), (decoded as GameDecodeResult.Valid).x01.match.colorSlots)
        assertEquals("colors=3 1 4", gameBody.single { it.startsWith("colors=") })
    }

    /** R17, PC8: the last setup keeps non-positional slots. */
    @Test
    fun `R17 a last setup keeps non-positional slots`() {
        val decoded = setupCodec.decode(setupCodec.encode(setup.toLastSetups()))

        assertEquals(setup, decoded?.x01Setup)
        assertEquals(listOf(SLOT_2, SLOT_4, SLOT_1), decoded?.roster?.colorSlots)
    }

    /** R18: every frozen version 1 game decodes with the slots of the player positions. */
    @Test
    fun `R18 version 1 games get slots by position`() {
        V1Fixtures.GAMES.keys.forEach { name ->
            val decoded = gameCodec.decode(V1Fixtures.bytes(name)) as GameDecodeResult.Valid
            val players = decoded.x01.match.config.players.size

            assertEquals(name, ColorSlot.byPosition(players), decoded.x01.match.colorSlots)
        }
    }

    /** R18: the frozen version 1 last setup decodes with the slots of the player positions. */
    @Test
    fun `R18 a version 1 last setup gets slots by position`() {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/persistence/v1/last-setup.save")).readBytes()

        assertEquals(ColorSlot.byPosition(3), setupCodec.decode(bytes)?.roster?.colorSlots)
    }

    /** S1, R9: one slot per player, separated by single spaces; another count or an empty token is malformed. */
    @Test
    fun `R9 a colors line with the wrong count is malformed`() {
        listOf("3 1", "3 1 4 2", "3  1 4", "3 1 4 ", "").forEach {
            assertGameInvalid(InvalidSaveReason.MALFORMED, gameBody.withColors(it))
        }
    }

    /** S1, R9: a slot outside 1 to 4, or not a canonical integer, is out of range. */
    @Test
    fun `R9 a slot value 0 or 5 is out of range`() {
        listOf("0 1 4", "3 5 4", "3 1 04", "3 x 4", "3 -1 4").forEach {
            assertGameInvalid(InvalidSaveReason.OUT_OF_RANGE, gameBody.withColors(it))
        }
    }

    /** S1, R9, PC1: two players with the same slot are out of range. */
    @Test
    fun `R9 a repeated slot is out of range`() {
        assertGameInvalid(InvalidSaveReason.OUT_OF_RANGE, gameBody.withColors("3 1 3"))
    }

    /** S1, S2, R9: a version 3 file without its colors line is malformed. */
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
        val extra = gameBody.flatMap { if (it.startsWith("colors=")) listOf(it, "colors=1 2 3") else listOf(it) }

        assertGameInvalid(InvalidSaveReason.MALFORMED, extra)
    }

    /** S2, R9: a version 2 file carrying the version 3 colors key is malformed. */
    @Test
    fun `R9 a version 2 file with a colors line is malformed`() {
        // The version 4 mode line is dropped too (R25), so that only the colors line is wrong.
        val version2 = gameBody.filterNot { it == "mode=x01" }.map { if (it == "version=4") "version=2" else it }

        assertGameInvalid(InvalidSaveReason.MALFORMED, version2)
    }

    /** S1, R10: the same invalid colors lines make the last setup unreadable. */
    @Test
    fun `R10 an invalid colors line makes the last setup unreadable`() {
        val colors = setupBody.single { it.startsWith("colors=") }
        listOf(
            setupBody.withColors("2 4"),
            setupBody.withColors("2 4 5"),
            setupBody.withColors("0 4 1"),
            setupBody.withColors("2 4 2"),
            setupBody - colors,
            setupBody + colors,
            // Version 2 with the colors key: the version 4 X01 lines without the mode line are the version 3 layout.
            setupBody.filterNot { it.startsWith("mode=") }.map { if (it == "version=4") "version=2" else it },
        ).forEach { assertNull(it.toString(), setupCodec.decode(withChecksum(it))) }
    }
}
