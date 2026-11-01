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

    /** ADR 0011, R26: the exact bytes of the same match in format version 1: the mode line, the body, the journal. */
    @Test
    fun `format v1 golden file of a match`() {
        val golden = """
            kosmodarts=saved-game
            version=1
            mode=x01
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
            match-id=00000000-0000-0000-0000-000000000000
            started-at=0
            journals=3
            journal=T20@0 T20@1 T20@2 0@3 0@4 0@5 T20@6 T19@7 D2@8
            journal=T20@9 T20@10 T20@11 0@12 0@13 0@14 T20@15 T19@16 D2@17
            journal=T20@18 20@19
            crc32=567c960a
            end

        """.trimIndent().toByteArray(Charsets.UTF_8)

        assertArrayEquals(golden, codec.encode(midLeg))
        assertEquals(GameDecodeResult.Valid(midLeg), codec.decode(golden))
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

        assertEquals(2, decoded.x01.game.startingPlayerIndex)
        assertEquals(2, decoded.x01.game.state.currentPlayerIndex)
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
        val awaitingLine = validBody.first { it.startsWith("awaiting-next-player=") }
        val betweenLine = validBody.first { it.startsWith("between-legs=") }
        listOf(
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
        assertInvalid(InvalidSaveReason.REJECTED_BY_ENGINE, JournalFixtures.withJournalCount(afterMatch, count = 4))
        // The confirmed legs decide the match: the deciding leg is never confirmed.
        val decided = afterMatch.filterNot { it == "leg=1 0/6 301/9" }.map { if (it == "legs=3") "legs=2" else it }
        assertInvalid(InvalidSaveReason.REJECTED_BY_ENGINE, JournalFixtures.withJournalCount(decided, count = 3))
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
