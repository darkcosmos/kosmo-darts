package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01LegSummary
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures.match
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Save format of a match: version 2 (docs/adr/0005-save-format-v2-match.md) and its version 3 successor
 * (docs/adr/0006-save-format-v3-colors.md); rules R9, R12, R13, R14, R16, R18; S1, S2 of the design review of #39.
 */
class GameSaveMatchCodecTest {

    private val codec = GameSaveCodec()

    private val config = X01Config(listOf("Ann", "Bob"), 301, CheckoutRule.SINGLE_OUT)

    /** First to 2 legs, 2 sets: Ann won leg 1, Bob leg 2; Ann has thrown T20 20 in leg 3. */
    private val midLeg = SavedGame(
        X01Match(
            X01MatchConfig(config, MatchFormat(2, 2)),
            listOf(
                X01LegSummary(config, 0, listOf(301, 120), listOf(9, 6)),
                X01LegSummary(config, 1, listOf(200, 301), listOf(9, 9)),
            ),
            X01Game(config, listOf(Dart.triple(20), Dart.single(20))),
        ),
        isAwaitingNextPlayer = false,
    )

    private val validBody: List<String> = coveredLines(checkNotNull(codec.encode(midLeg)))

    private fun assertRoundTrip(saved: SavedGame) {
        val bytes = checkNotNull(codec.encode(saved))

        assertEquals(GameDecodeResult.Valid(saved), codec.decode(bytes))
    }

    private fun assertInvalid(expected: InvalidSaveReason, lines: List<String>) {
        assertEquals(lines.toString(), GameDecodeResult.Invalid(expected), codec.decode(withChecksum(lines)))
    }

    /** Replaces the line of [key] (the first one) in the valid body. */
    private fun withLine(key: String, line: String): List<String> {
        val index = validBody.indexOfFirst { it.startsWith("$key=") }
        return validBody.toMutableList().apply { set(index, line) }
    }

    /**
     * ADR 0005: the exact bytes of a match in the middle of a leg, as the version 2 encoder wrote them. Frozen since
     * version 3 (ADR 0006, R18): it keeps decoding the same, with the color slots of the player positions.
     */
    @Test
    fun `format v2 golden file of a match`() {
        val golden = """
            kosmodarts=saved-game
            version=2
            game=x01
            start-score=301
            checkout=single-out
            legs-per-set=2
            sets=2
            players=2
            player=QW5u
            player=Qm9i
            legs=2
            leg=0 301/9 120/6
            leg=1 200/9 301/9
            darts=T20 20
            awaiting-next-player=false
            between-legs=false
            crc32=03fea0a7
            end

        """.trimIndent().toByteArray(Charsets.UTF_8)

        assertEquals(GameDecodeResult.Valid(midLeg), codec.decode(golden))
    }

    /** ADR 0006: the exact bytes of the same match in version 3, the color slots after the player lines. */
    @Test
    fun `format v3 golden file of a match`() {
        val golden = """
            kosmodarts=saved-game
            version=3
            game=x01
            start-score=301
            checkout=single-out
            legs-per-set=2
            sets=2
            players=2
            player=QW5u
            player=Qm9i
            colors=1 2
            legs=2
            leg=0 301/9 120/6
            leg=1 200/9 301/9
            darts=T20 20
            awaiting-next-player=false
            between-legs=false
            crc32=551cd129
            end

        """.trimIndent().toByteArray(Charsets.UTF_8)

        assertArrayEquals(golden, codec.encode(midLeg))
        assertEquals(GameDecodeResult.Valid(midLeg), codec.decode(golden))
    }

    /** R16: every version 1 file decodes as a single game (1 and 1, no confirmed leg, not between legs). */
    @Test
    fun `R16 version 1 files decode as single games`() {
        V1Fixtures.GAMES.forEach { (name, saved) ->
            val decoded = codec.decode(V1Fixtures.bytes(name))

            assertEquals(name, GameDecodeResult.Valid(saved), decoded)
            val match = (decoded as GameDecodeResult.Valid).saved.match
            assertEquals(name, MatchFormat(), match.format)
            assertTrue(name, match.completedLegs.isEmpty())
            assertEquals(name, false, decoded.saved.isBetweenLegs)
        }
    }

    /**
     * T4: a single game re-encodes to exactly its version 1 content, with `version=3` and the new keys inserted at
     * their places (legs-per-set and sets after checkout, colors by position and legs before darts, between-legs after
     * the waiting flag; R18).
     */
    @Test
    fun `R13 a single game is its version 1 content plus the new keys`() {
        V1Fixtures.GAMES.keys.forEach { name ->
            val v1 = coveredLines(V1Fixtures.bytes(name))
            val players = v1.first { it.startsWith("players=") }.substringAfter('=').toInt()
            val colors = "colors=" + (1..players).joinToString(" ")
            val expected = v1.flatMap { line ->
                when {
                    line == "version=1" -> listOf("version=3")
                    line.startsWith("checkout=") -> listOf(line, "legs-per-set=1", "sets=1")
                    line.startsWith("darts=") -> listOf(colors, "legs=0", line)
                    line.startsWith("awaiting-next-player=") -> listOf(line, "between-legs=false")
                    else -> listOf(line)
                }
            }
            val decoded = codec.decode(V1Fixtures.bytes(name)) as GameDecodeResult.Valid

            assertEquals(name, expected, coveredLines(checkNotNull(codec.encode(decoded.saved))))
        }
    }

    /** R14: in the middle of a leg, in the middle of a set, after a set, between legs and in each checkout dialog. */
    @Test
    fun `R14 matches round trip at every stage`() {
        val start = match("Ann", "Bob", "Cid", legsPerSet = 2, sets = 2)
        val afterOneLeg = start.confirmLegsWonBy(1)
        val afterSet = afterOneLeg.confirmLegsWonBy(1)
        listOf(
            SavedGame(start.throwing(Dart.triple(20)), isAwaitingNextPlayer = false),
            SavedGame(afterOneLeg.throwing(Dart.single(5), Dart.single(5)), isAwaitingNextPlayer = false),
            SavedGame(afterOneLeg.throwing(Dart.MISS, Dart.MISS, Dart.MISS), isAwaitingNextPlayer = true),
            SavedGame(afterSet, isAwaitingNextPlayer = false),
            SavedGame(afterOneLeg, isAwaitingNextPlayer = false, isBetweenLegs = true),
            SavedGame(afterSet, isAwaitingNextPlayer = false, isBetweenLegs = true),
            SavedGame(start.playLegWonBy(0), isAwaitingNextPlayer = true), // LEG pending.
            SavedGame(afterOneLeg.playLegWonBy(1), isAwaitingNextPlayer = true), // SET pending.
            SavedGame(afterSet.confirmLegsWonBy(1).playLegWonBy(1), isAwaitingNextPlayer = true), // MATCH pending.
        ).forEach(::assertRoundTrip)
    }

    /** R14: the starter of the current leg is not stored: it is derived from the number of confirmed legs (M6). */
    @Test
    fun `R14 the current leg is replayed with its starter`() {
        val saved = SavedGame(match("Ann", "Bob", "Cid", legsPerSet = 3).confirmLegsWonBy(0, 0), false)

        val decoded = codec.decode(checkNotNull(codec.encode(saved))) as GameDecodeResult.Valid

        assertEquals(2, decoded.saved.game.startingPlayerIndex)
        assertEquals(2, decoded.saved.game.state.currentPlayerIndex)
    }

    /** ADR 0005: between-legs only right after a confirmed leg, before any dart, with nothing waiting. */
    @Test
    fun `R9 an inconsistent between-legs flag is rejected`() {
        val start = match("Ann", "Bob", legsPerSet = 2)
        listOf(
            SavedGame(start, isAwaitingNextPlayer = false, isBetweenLegs = true), // No confirmed leg.
            SavedGame(start.confirmLegsWonBy(0).throwing(Dart.single(1)), false, isBetweenLegs = true), // A dart.
            SavedGame(start.confirmLegsWonBy(0), isAwaitingNextPlayer = true, isBetweenLegs = true), // Waiting.
        ).forEach { saved ->
            assertEquals(
                saved.toString(),
                GameDecodeResult.Invalid(InvalidSaveReason.INCONSISTENT_WAITING_FLAG),
                codec.decode(checkNotNull(codec.encode(saved))),
            )
        }
        assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withLine("between-legs", "between-legs=yes"))
    }

    /** S1, R9: leg values outside the engine ranges are out of range. */
    @Test
    fun `R9 impossible confirmed legs are out of range`() {
        listOf(
            "leg=2 301/9 120/6", // Winner out of range.
            "leg=0 301/9 301/6", // A non-winner at the start score (S1).
            "leg=0 300/9 120/6", // The winner below the start score.
            "leg=0 301/9 302/9", // A non-winner above the start score.
            "leg=0 301/5 120/6", // More than 60 points per dart.
            "leg=0 301/9 120/10001", // More darts than saved (R12).
            "leg=0 301/09 120/6", // Not a canonical integer.
            "leg=0 301/9 -1/6",
        ).forEach { assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withLine("leg", it)) }
    }

    /** M1, M12, R9: format values and the number of legs are bounded. */
    @Test
    fun `R9 out of range format and leg count`() {
        listOf("legs-per-set=0", "legs-per-set=10", "sets=0", "sets=10", "sets=x").forEach {
            assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withLine(it.substringBefore('='), it))
        }
        // First to 2 legs, 2 sets, 2 players: at most 9 legs (M12).
        val format = MatchFormat(2, 2)
        assertEquals(9, format.maxLegs(2))
        assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withLine("legs", "legs=10"))
        assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withLine("legs", "legs=100000"))
    }

    /** R9: a leg line that does not have one figure per player, or a figure without its separator, is malformed. */
    @Test
    fun `R9 malformed leg lines`() {
        listOf(
            "leg=0 301/9",
            "leg=0 301/9 120/6 1/1",
            "leg=0 301/9 120-6",
            "leg=0 301/9/1 120/6",
            "leg=0  301/9 120/6",
            "leg=",
        ).forEach { assertInvalid(InvalidSaveReason.MALFORMED, withLine("leg", it)) }
        // Fewer leg lines than announced.
        assertInvalid(InvalidSaveReason.MALFORMED, validBody.filterNot { it.startsWith("leg=1 ") })
    }

    /** S2: the keys of each version, in their order, each exactly once. */
    @Test
    fun `R9 keys must follow the layout of their version`() {
        val v1Single = coveredLines(V1Fixtures.bytes("game-golden.save"))
        val checkoutAt = v1Single.indexOfFirst { it.startsWith("checkout=") }
        val awaitingLine = validBody.first { it.startsWith("awaiting-next-player=") }
        val betweenLine = validBody.first { it.startsWith("between-legs=") }
        listOf(
            // Version 1 with a version 2 key.
            v1Single.take(checkoutAt + 1) + "legs-per-set=1" + v1Single.drop(checkoutAt + 1),
            v1Single + "between-legs=false",
            // Version 3 with a missing, duplicated or out-of-order key.
            validBody - betweenLine,
            validBody.filterNot { it.startsWith("sets=") },
            validBody + betweenLine,
            validBody.filterNot { it == awaitingLine || it == betweenLine } + betweenLine + awaitingLine,
            withLine("legs-per-set", "sets=2").let { lines ->
                lines.toMutableList().apply { set(indexOfLast { it == "sets=2" }, "legs-per-set=2") }
            },
        ).forEach { assertInvalid(InvalidSaveReason.MALFORMED, it) }
    }

    /** M4, R9: confirmed legs that go on after the match is decided, or decide it, are rejected by the engine. */
    @Test
    fun `R9 legs after the match is decided are rejected by the engine`() {
        // First to 2 legs, one set: Ann's second win decides the match, so no leg may follow it.
        val afterMatch = withLine("legs-per-set", "legs-per-set=2").let { lines ->
            lines.map { if (it == "sets=2") "sets=1" else it }
                .filterNot { it.startsWith("leg=") }
                .flatMap {
                    if (it.startsWith("legs=")) {
                        listOf("legs=3", "leg=0 301/9 0/6", "leg=0 301/9 0/6", "leg=1 0/6 301/9")
                    } else {
                        listOf(it)
                    }
                }
        }
        assertInvalid(InvalidSaveReason.REJECTED_BY_ENGINE, afterMatch)
        // The confirmed legs decide the match: the deciding leg is never confirmed.
        val decided = afterMatch.filterNot { it == "leg=1 0/6 301/9" }.map { if (it == "legs=3") "legs=2" else it }
        assertInvalid(InvalidSaveReason.REJECTED_BY_ENGINE, decided)
    }

    /** R12: a confirmed leg with more darts than saved is not encoded, so the previous file stays. */
    @Test
    fun `R12 a confirmed leg over 10,000 darts is not encoded`() {
        val tooLong = X01LegSummary(config, 0, listOf(301, 0), listOf(GameSaveCodec.MAX_DARTS + 1, 0))
        val saved = SavedGame(
            X01Match(
                X01MatchConfig(config, MatchFormat(2, 1)),
                listOf(tooLong),
                X01Game(config, startingPlayerIndex = 1),
            ),
            isAwaitingNextPlayer = false,
        )

        assertNull(codec.encode(saved))
    }

    /**
     * A2: the largest valid save is accepted: 4 names of 20 emoji, first to 9 legs and 9 sets, the longest leg
     * sequence (1,088 confirmed legs, the 1,089th is the current one) with the widest leg lines, and 10,000 triple 20
     * (which bust forever) in the current leg.
     */
    @Test
    fun `R12 the largest valid save is under the size limit and decodes`() {
        val names = listOf("🎯", "🏆", "🍺", "🎲").map { it.repeat(X01Config.MAX_NAME_LENGTH) }
        val largest = X01Config(names, 501, CheckoutRule.DOUBLE_OUT)
        val format = MatchFormat(MatchFormat.MAX, MatchFormat.MAX)
        val legs = longestWinners(format, players = 4).map { winner ->
            X01LegSummary(
                largest,
                winner,
                List(4) { if (it == winner) 501 else 500 },
                List(4) { GameSaveCodec.MAX_DARTS },
            )
        }
        assertEquals(format.maxLegs(4) - 1, legs.size)
        val current = X01Game(largest, List(GameSaveCodec.MAX_DARTS) { Dart.triple(20) }, legs.size % 4)
        val saved = SavedGame(X01Match(X01MatchConfig(largest, format), legs, current), isAwaitingNextPlayer = false)

        val bytes = checkNotNull(codec.encode(saved))

        assertTrue("${bytes.size} bytes", bytes.size <= GameSaveCodec.MAX_FILE_BYTES)
        assertEquals(GameDecodeResult.Valid(saved), codec.decode(bytes))
    }

    /**
     * The longest winner sequence before the deciding leg: in each set, every player wins L-1 legs in turn, then the
     * set goes to the next player in turn, so that every player reaches S-1 sets. The deciding leg is not included.
     */
    private fun longestWinners(format: MatchFormat, players: Int): List<Int> {
        val setWinners = List(players * (format.sets - 1)) { it % players } + 0
        return setWinners.flatMapIndexed { set, setWinner ->
            val legs = List(format.legsPerSet - 1) { (0 until players).toList() }.flatten() + setWinner
            if (set == setWinners.lastIndex) legs.dropLast(1) else legs
        }
    }
}
