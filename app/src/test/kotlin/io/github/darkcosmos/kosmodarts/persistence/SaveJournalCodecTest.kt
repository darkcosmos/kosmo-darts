package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.journal.JournalEvent
import io.github.darkcosmos.kosmodarts.engine.journal.LegJournal
import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The match id, the start time and the dart journal of the saved game (ADR 0011; rules R9, R10, R26 to R30, D24, D25).
 * Every name and time is made up.
 */
class SaveJournalCodecTest {

    private val codec = GameSaveCodec()

    private val config = X01Config(listOf("Ann", "Bob"))

    /** Ann's second dart, a 5, was undone and replaced by a 20. */
    private val voidedGame: SavedGame = SavedGame(
        X01Match(
            X01MatchConfig(config),
            emptyList(),
            X01Game(config, listOf(Dart.triple(20), Dart.single(20), Dart.double(5))),
        ),
        isAwaitingNextPlayer = true,
        journal = journal(
            LegJournal(
                listOf(
                    JournalFixtures.dart(Dart.triple(20), 1_793_520_001_200),
                    JournalFixtures.dart(Dart.single(5), 1_793_520_003_400),
                    JournalEvent.Undone,
                    JournalFixtures.dart(Dart.single(20), 1_793_520_005_000),
                    JournalFixtures.dart(Dart.double(5), 1_793_520_006_100),
                ),
            ),
        ),
    )

    private val voidedLines: List<String> = coveredLines(checkNotNull(codec.encode(voidedGame)))

    private fun journal(vararg legs: LegJournal) =
        MatchJournal(JournalFixtures.MATCH_ID, JournalFixtures.STARTED_AT, legs.toList())

    private fun assertInvalid(reason: InvalidSaveReason, bytes: ByteArray) =
        assertEquals(GameDecodeResult.Invalid(reason), codec.decode(bytes))

    private fun assertInvalid(reason: InvalidSaveReason, lines: List<String>) =
        assertInvalid(reason, withChecksum(lines))

    private fun withJournalLine(line: String): List<String> =
        voidedLines.map { if (it.startsWith("journal=")) line else it }

    private fun withLine(key: String, line: String): List<String> =
        voidedLines.map { if (it.startsWith("$key=")) line else it }

    private fun assertRoundTrip(saved: StoredGame) {
        assertEquals(GameDecodeResult.Valid(saved), codec.decode(checkNotNull(codec.encode(saved))))
    }

    /** A game of a match of 5 legs per set with three confirmed legs, each journal padded with voided darts. */
    private fun paddedGame(pairsPerLeg: List<Int>): SavedGame {
        val winners = listOf(0, 1, 0)
        var match = MatchFixtures.match("Ann", "Bob", legsPerSet = 5)
        val legs = winners.mapIndexed { index, winner ->
            val darts = MatchFixtures.dartsToWin(match, winner)
            match = match.playLegWonBy(winner).confirmLeg()
            padded(pairsPerLeg[index], darts)
        }
        val current = padded(pairsPerLeg.last(), listOf(Dart.MISS))
        return SavedGame(match.throwing(Dart.MISS), false, journal = journal(*(legs + current).toTypedArray()))
    }

    /** [pairs] voided darts, then these darts one second apart. Times have 13 digits, so a pair takes 20 bytes. */
    private fun padded(pairs: Int, darts: List<Dart>): LegJournal {
        val voidedDart = JournalFixtures.dart(Dart.triple(20), JournalFixtures.FIRST_DART_AT)
        val voided = List(pairs) { listOf(voidedDart, JournalEvent.Undone) }
        return LegJournal(voided.flatten() + JournalFixtures.leg(darts).events)
    }

    /** R26, D24: voided darts and entry times come back as they were stored. */
    @Test
    fun `R26 a single game with a voided dart round trips`() = assertRoundTrip(voidedGame)

    /** R26: several legs, voided darts in each, the current leg after them. */
    @Test
    fun `R26 a match of several legs with voided darts round trips`() = assertRoundTrip(paddedGame(listOf(2, 0, 1, 3)))

    /** R26, R20: a Cricket match with a voided dart. */
    @Test
    fun `R26 a Cricket match with a voided dart round trips`() {
        val match = CricketSaveFixtures.MID_TURN.match
        val events = listOf(
            JournalFixtures.dart(Dart.MISS, 1_793_520_000_900),
            JournalEvent.Undone,
            JournalFixtures.dart(Dart.triple(20), 1_793_520_001_200),
            JournalFixtures.dart(Dart.single(19), 1_793_520_002_200),
        )

        assertRoundTrip(SavedCricketGame(match, isAwaitingNextPlayer = false, journal = journal(LegJournal(events))))
    }

    /** R27: the largest time and the first time are valid. */
    @Test
    fun `R27 the first and the largest times round trip`() {
        val saved = voidedGame.copy(
            journal = MatchJournal(
                JournalFixtures.MATCH_ID,
                Long.MAX_VALUE,
                voidedGame.journal.legs,
            ),
        )

        assertRoundTrip(saved)
        assertRoundTrip(voidedGame.copy(journal = saved.journal.copy(startedAt = 0)))
    }

    /** R30, R9: version 1 is the only version; every other number is unsupported. */
    @Test
    fun `R30 only version 1 is supported`() {
        listOf("0", "2", "3", "4", "5").forEach { version ->
            val lines = voidedLines.map { if (it == "version=1") "version=$version" else it }
            assertInvalid(InvalidSaveReason.UNSUPPORTED_VERSION, lines)
        }
    }

    /** R30: a version 1 file without the journal lines is malformed. */
    @Test
    fun `R30 a version 1 file without journal lines is malformed`() {
        val withoutJournal = voidedLines.filterNot {
            it.startsWith("match-id=") || it.startsWith("started-at=") || it.startsWith("journals=") ||
                it.startsWith("journal=")
        }

        assertInvalid(InvalidSaveReason.MALFORMED, withoutJournal)
        assertInvalid(InvalidSaveReason.MALFORMED, voidedLines.filterNot { it.startsWith("journal=") })
    }

    /** R30, R9: a file in the layout of the old version 1 (no mode line, no journal) is rejected like any other. */
    @Test
    fun `R30 a file in the layout of the old version 1 is rejected`() {
        val old = listOf(
            "kosmodarts=saved-game",
            "version=1",
            "game=x01",
            "start-score=501",
            "checkout=double-out",
            "players=2",
            "player=QW5u",
            "player=Q2zDqW1lbnQ=",
            "darts=T20 20 D5",
            "awaiting-next-player=true",
        )

        assertInvalid(InvalidSaveReason.MALFORMED, old)
    }

    /** R27: every dart has an entry time, written as `@` and a number. */
    @Test
    fun `R27 a dart without an entry time is malformed`() {
        listOf(
            "T20@-",
            "T20",
            "T20@",
            "@-",
            "@5",
            "T20@5@6",
        ).forEach { event ->
            assertInvalid(InvalidSaveReason.MALFORMED, withJournalLine("journal=$event 20@1 D5@2"))
            assertInvalid(InvalidSaveReason.MALFORMED, withJournalLine("journal=T20@1 20@2 $event"))
        }
    }

    /** R27: a token that is not a dart of the board is out of range. */
    @Test
    fun `R27 an unknown dart token is out of range`() {
        listOf("T21@5", "D0@5", "26@5", "x@5").forEach { event ->
            assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withJournalLine("journal=$event 20@6 D5@7"))
        }
    }

    /** R27: strict numbers, never a NumberFormatException. */
    @Test
    fun `R27 malformed numbers are malformed`() {
        listOf(
            "12345678901234567890",
            "9223372036854775808",
            "-0",
            "+1",
            "007",
            "-5",
            "1.5",
            "1e3",
            "",
            " 5",
        ).forEach { number ->
            assertInvalid(InvalidSaveReason.MALFORMED, withLine("started-at", "started-at=$number"))
            assertInvalid(InvalidSaveReason.MALFORMED, withJournalLine("journal=T20@$number 20@6 D5@7"))
        }
    }

    /** R27: the match id is a lower-case UUID. */
    @Test
    fun `R27 an invalid match id is malformed`() {
        listOf(
            "NOT-A-UUID",
            "",
            "1B4E28BA-2D1C-41F1-9B7E-6A2F1C0D9E55",
            "1b4e28ba2d1c41f19b7e6a2f1c0d9e55",
            "1b4e28ba-2d1c-41f1-9b7e-6a2f1c0d9e5",
            "1b4e28ba-2d1c-41f1-9b7e-6a2f1c0d9e55 ",
        ).forEach { id ->
            assertInvalid(InvalidSaveReason.MALFORMED, withLine("match-id", "match-id=$id"))
        }
    }

    /** R29: one journal per confirmed leg plus the current one. */
    @Test
    fun `R29 a wrong number of journals is inconsistent`() {
        val twoJournals = voidedLines.flatMap {
            if (it.startsWith("journal=")) listOf(it, "journal=T20@1") else listOf(it)
        }.map { if (it == "journals=1") "journals=2" else it }

        assertInvalid(InvalidSaveReason.INCONSISTENT_JOURNAL, twoJournals)
    }

    /** R29, D25: the darts left in play by the journal are the darts of the saved leg. */
    @Test
    fun `R29 a journal that disagrees with the darts of the leg is inconsistent`() {
        assertInvalid(InvalidSaveReason.INCONSISTENT_JOURNAL, withJournalLine("journal=T20@1 20@2 D6@3"))
        assertInvalid(InvalidSaveReason.INCONSISTENT_JOURNAL, withJournalLine("journal=T20@1 20@2"))
        assertInvalid(InvalidSaveReason.INCONSISTENT_JOURNAL, withJournalLine("journal=T20@1 20@2 D5@3 u"))
    }

    /** R29: an undo with nothing to undo is rejected by the engine. */
    @Test
    fun `R29 an undo with nothing to undo is rejected by the engine`() {
        val events = listOf(JournalEvent.Undone) + voidedGame.journal.legs[0].events
        val nothing = voidedGame.copy(journal = journal(LegJournal(events)))

        assertInvalid(InvalidSaveReason.REJECTED_BY_ENGINE, checkNotNull(codec.encode(nothing)))
    }

    /** R29: a dart after the win is rejected by the engine, even if it is voided at once. */
    @Test
    fun `R29 a dart after the win is rejected by the engine`() {
        val one = X01Config(listOf("Ann"), 301, CheckoutRule.SINGLE_OUT)
        val winning = List(5) { Dart.triple(20) } + Dart.single(1)
        val match = X01Match(X01MatchConfig(one), emptyList(), X01Game(one, winning))
        val events = JournalFixtures.leg(winning).events

        val ok = SavedGame(match, isAwaitingNextPlayer = true, journal = journal(LegJournal(events)))
        assertRoundTrip(ok)

        val afterWin = events + JournalFixtures.dart(Dart.MISS, 1_793_520_099_000) + JournalEvent.Undone
        val bad = ok.copy(journal = journal(LegJournal(afterWin)))
        assertInvalid(InvalidSaveReason.REJECTED_BY_ENGINE, checkNotNull(codec.encode(bad)))
    }

    /** R29: the journal of a confirmed leg ends with the winner of that leg. */
    @Test
    fun `R29 a confirmed leg whose journal has another winner is inconsistent`() {
        val start = MatchFixtures.match("Ann", "Bob", legsPerSet = 2)
        val match = start.confirmLegsWonBy(0)
        val misses = JournalFixtures.leg(List(3) { Dart.MISS })
        val wrongWinner = JournalFixtures.leg(MatchFixtures.dartsToWin(start, 1))
        val noWinner = SavedGame(match, false, journal = journal(misses, LegJournal(emptyList())))
        val other = SavedGame(match, false, journal = journal(wrongWinner, LegJournal(emptyList())))
        val right = SavedGame(
            match,
            false,
            journal = journal(JournalFixtures.leg(MatchFixtures.dartsToWin(start, 0)), LegJournal(emptyList())),
        )

        assertInvalid(InvalidSaveReason.INCONSISTENT_JOURNAL, checkNotNull(codec.encode(noWinner)))
        assertInvalid(InvalidSaveReason.INCONSISTENT_JOURNAL, checkNotNull(codec.encode(other)))
        assertRoundTrip(right)
    }

    /** R28: a leg journal of 30,000 events is the largest; one more is out of range and not written. */
    @Test
    fun `R28 a leg journal over 30,000 events is out of range`() {
        val pair = "T20@5 u"
        val overCap = List(15_000) { pair }.joinToString(" ") + " T20@5"

        val empty = SavedGame(X01Game(config), isAwaitingNextPlayer = false)
        val capGame = empty.copy(journal = journal(padded(15_000, emptyList())))
        val overEvents = padded(15_000, emptyList()).events + JournalFixtures.dart(Dart.MISS, 1)
        val overGame = empty.copy(journal = journal(LegJournal(overEvents)))

        assertRoundTrip(capGame)
        assertNull(codec.encode(overGame))
        val emptyLines = coveredLines(checkNotNull(codec.encode(empty)))
        assertInvalid(
            InvalidSaveReason.OUT_OF_RANGE,
            emptyLines.map { if (it.startsWith("journal=")) "journal=$overCap" else it },
        )
    }

    /** R28: a file of exactly the size limit or less is written; one dart event more is not. */
    @Test
    fun `R28 a file just under 1 MiB round trips and one just over is not encoded`() {
        val base = checkNotNull(codec.encode(paddedGame(listOf(0, 0, 0, 0)))).size
        val pairs = (GameSaveCodec.MAX_FILE_BYTES - base) / BYTES_PER_PAIR
        val perLeg = listOf(pairs / 4 + pairs % 4, pairs / 4, pairs / 4, pairs / 4)

        val under = paddedGame(perLeg)
        val over = paddedGame(listOf(perLeg[0] + 1) + perLeg.drop(1))

        val bytes = checkNotNull(codec.encode(under))
        assertTrue(bytes.size <= GameSaveCodec.MAX_FILE_BYTES)
        assertTrue(bytes.size > GameSaveCodec.MAX_FILE_BYTES - BYTES_PER_PAIR)
        assertEquals(GameDecodeResult.Valid(under), codec.decode(bytes))
        assertNull(codec.encode(over))
    }

    /** R12, R28: 10,000 darts in play and 500 voided darts before them still encode. */
    @Test
    fun `R12 10,000 darts in play and 500 voided darts round trip`() {
        val darts = List(GameSaveCodec.MAX_DARTS) { Dart.MISS }
        val legJournal = padded(500, darts)
        val saved = SaveFixtures.LONGEST.copy(journal = journal(legJournal))

        assertRoundTrip(saved)
        assertEquals(GameSaveCodec.MAX_DARTS + 1_000, legJournal.events.size)
    }

    /**
     * ADR 0011, "Limits": about 58,000 events over four legs. The decode time is logged, never asserted: it depends on
     * the machine.
     */
    @Test
    fun `R28 a file of 58,000 events decodes without replaying on every event`() {
        val saved = paddedGame(List(4) { 7_250 })
        val bytes = checkNotNull(codec.encode(saved))

        val start = System.nanoTime()
        val result = codec.decode(bytes)
        val elapsedMs = (System.nanoTime() - start) / NANOS_PER_MILLI

        val events = saved.journal.legs.sumOf { it.events.size }
        println("decode of ${bytes.size} bytes and $events events: $elapsedMs ms")
        assertEquals(GameDecodeResult.Valid(saved), result)
    }

    private companion object {
        /** `T20@` and 13 digits, a space, `u` and a space. */
        const val BYTES_PER_PAIR = 20
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
