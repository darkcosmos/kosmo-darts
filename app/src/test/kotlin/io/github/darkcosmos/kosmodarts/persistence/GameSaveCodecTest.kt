package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.journal.JournalEvent
import io.github.darkcosmos.kosmodarts.engine.journal.LegJournal
import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures.config
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures.savedAfter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Encoding and round trips of the saved game (docs/adr/0001-save-format.md; rules R4, R5, R12). */
class GameSaveCodecTest {

    private val codec = GameSaveCodec()

    private fun assertRoundTrip(saved: SavedGame) {
        val bytes = checkNotNull(codec.encode(saved))

        assertEquals(GameDecodeResult.Valid(saved), codec.decode(bytes))
    }

    /** R4: every representative game comes back equal, including its waiting flag. */
    @Test
    fun `R4 representative games round trip`() {
        listOf(
            SaveFixtures.EMPTY,
            SaveFixtures.MID_TURN,
            SaveFixtures.WAITING_NORMAL,
            SaveFixtures.AFTER_NEXT_PLAYER,
            SaveFixtures.WAITING_BUST,
            SaveFixtures.WAITING_BUST_FIRST_DART,
            SaveFixtures.WAITING_CHECKOUT,
        ).forEach(::assertRoundTrip)
    }

    /** R4: 1 to 4 players, 501 and 301, double and single out. */
    @Test
    fun `R4 every player count, start score and checkout rule round trips`() {
        val darts = listOf(Dart.triple(20), Dart.double(19), Dart.single(1), Dart.OUTER_BULL, Dart.BULL, Dart.MISS)
        for (players in 1..X01Config.MAX_PLAYERS) {
            for (startScore in X01Config.START_SCORES) {
                for (rule in CheckoutRule.entries) {
                    assertRoundTrip(savedAfter(config(players, startScore, rule), darts))
                }
            }
        }
    }

    /** R4: every dart of the board has a token. */
    @Test
    fun `R4 every dart round trips`() {
        val everyDart = Dart.NUMBERS.flatMap { listOf(Dart.single(it), Dart.double(it), Dart.triple(it)) } +
            listOf(Dart.OUTER_BULL, Dart.BULL, Dart.MISS)
        // A single dart never finishes 501, so each one is a valid game on its own.
        everyDart.forEach { dart ->
            assertRoundTrip(SavedGame(X01Game(config(players = 1)).throwDart(dart), isAwaitingNextPlayer = false))
        }
    }

    /** R4: names are stored as the engine normalizes them, whatever their characters. */
    @Test
    fun `R4 names with accents, emoji, equal sign and 20 code points round trip`() {
        val names = listOf(
            "Clément",
            "Zoé 🎯",
            "a=b c",
            "ÉÉÉÉÉÉÉÉÉÉÉÉÉÉÉÉÉÉÉÉ", // 20 code points, 2 UTF-8 bytes each.
        )
        assertRoundTrip(SavedGame(X01Game(X01Config(names)), isAwaitingNextPlayer = false))
        // Decomposed input: the engine stores it in NFC, and that is what round trips.
        val decomposed = X01Config(listOf("Cle\u0301ment", "Bob"))
        assertRoundTrip(SavedGame(X01Game(decomposed), isAwaitingNextPlayer = false))
        // 20 emoji: 4 UTF-8 bytes each, the longest name accepted.
        assertRoundTrip(SavedGame(X01Game(X01Config(listOf("🎯".repeat(20)))), isAwaitingNextPlayer = false))
    }

    /** R12: the largest saved game. */
    @Test
    fun `R12 exactly 10,000 darts round trip`() {
        assertRoundTrip(SaveFixtures.LONGEST)
    }

    /** R12: beyond the limit, nothing is encoded, so the previous file stays. */
    @Test
    fun `R12 10,001 darts are not encoded`() {
        val tooLong = SavedGame(SaveFixtures.LONGEST.game.throwDart(Dart.MISS), isAwaitingNextPlayer = false)

        assertNull(codec.encode(tooLong))
    }

    /** A single game whose players hold slots 2 and 1, waiting for "Next player". */
    private val singleGameWithSlots: SavedGame = run {
        val config = X01Config(listOf("Ann", "Clément"))
        val match = X01Match(
            X01MatchConfig(config, MatchFormat(), listOf(ColorSlot.SLOT_2, ColorSlot.SLOT_1)),
            emptyList(),
            X01Game(config, listOf(Dart.triple(20), Dart.single(20), Dart.double(5))),
        )
        // Ann's second dart, a 5, was undone and replaced by a 20.
        val journal = MatchJournal(
            JournalFixtures.MATCH_ID,
            JournalFixtures.STARTED_AT,
            listOf(
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
        SavedGame(match, isAwaitingNextPlayer = true, journal = journal)
    }

    /** ADR 0011, R26: format version 1 of the same game with a voided dart, exact bytes: the body, then the journal. */
    @Test
    fun `format v1 golden file of a single game`() {
        val golden = """
            kosmodarts=saved-game
            version=1
            mode=x01
            game=x01
            start-score=501
            checkout=double-out
            legs-per-set=1
            sets=1
            players=2
            player=QW5u
            player=Q2zDqW1lbnQ=
            colors=2 1
            legs=0
            darts=T20 20 D5
            awaiting-next-player=true
            between-legs=false
            match-id=1b4e28ba-2d1c-41f1-9b7e-6a2f1c0d9e55
            started-at=1793520000000
            journals=1
            journal=T20@1793520001200 5@1793520003400 u 20@1793520005000 D5@1793520006100
            crc32=863f6d03
            end

        """.trimIndent().toByteArray(Charsets.UTF_8)

        assertArrayEquals(golden, codec.encode(singleGameWithSlots))
        assertEquals(GameDecodeResult.Valid(singleGameWithSlots), codec.decode(golden))
    }

    /** ADR 0002, decision 3: a save is replayed once, in a single engine call, never dart by dart. */
    @Test
    fun `R12 decoding 10,000 darts builds the game once`() {
        var builds = 0
        val counting = GameSaveCodec { config, darts, starter -> builds++; X01Game(config, darts, starter) }
        val bytes = checkNotNull(counting.encode(SaveFixtures.LONGEST))

        counting.decode(bytes)

        assertEquals(1, builds)
    }
}
