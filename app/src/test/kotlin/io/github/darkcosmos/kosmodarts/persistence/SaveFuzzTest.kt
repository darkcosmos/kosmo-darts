package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.journal.JournalEvent
import io.github.darkcosmos.kosmodarts.engine.journal.LegJournal
import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/**
 * R11: no file content can crash the app. Seeded random mutations of valid saves and random bytes never make the
 * decoders throw, and anything accepted is a canonical file. On failure, the message gives the seed, the iteration and
 * the input as hex; the fixtures only hold made-up names, and nothing is logged.
 */
class SaveFuzzTest {

    private val gameCodec = GameSaveCodec()
    private val setupCodec = LastSetupCodec()

    private val validGames: List<ByteArray> = listOf(
        SaveFixtures.EMPTY,
        SaveFixtures.MID_TURN,
        SaveFixtures.WAITING_NORMAL,
        SaveFixtures.WAITING_BUST,
        SaveFixtures.WAITING_CHECKOUT,
    ).map { checkNotNull(gameCodec.encode(it)) } + matchSeeds() + cricketSeeds() +
        journalSeeds()

    /**
     * Version 1 Cricket saves (R23, R24): mid-turn, waiting, a match in a leg, between legs, a pending win by a
     * non-thrower, cut-throat solo and 25-Bull off, plus the mode-line cases of R23 (no mode line, a mode line that
     * does not match its body, a file of an earlier version).
     */
    private fun cricketSeeds(): List<ByteArray> {
        val valid = listOf(
            CricketSaveFixtures.MID_TURN,
            CricketSaveFixtures.WAITING,
            CricketSaveFixtures.MATCH_MID_LEG,
            CricketSaveFixtures.BETWEEN_LEGS,
            CricketSaveFixtures.NON_THROWER_WIN,
            CricketSaveFixtures.saved(
                CricketSaveFixtures.match("Ann", variant = CricketVariant.CUT_THROAT, isBullInPlay = false)
                    .throwing(Dart.triple(20), Dart.double(15)),
            ),
        ).map { checkNotNull(gameCodec.encode(it)) }
        val cricket = coveredLines(valid.first())
        val x01 = coveredLines(checkNotNull(gameCodec.encode(SaveFixtures.WAITING_NORMAL)))
        val modeCases = listOf(
            cricket.filterNot { it.startsWith("mode=") },
            cricket.map { if (it == "mode=cricket") "mode=x01" else it },
            x01.map { if (it == "mode=x01") "mode=cricket" else it },
            x01.map { if (it == "version=1") "version=4" else it },
        ).map(::withChecksum)
        return valid + modeCases
    }

    /**
     * Matches (R11, ADR 0005, ADR 0006) with non-positional color slots: in a leg, between legs and in a pending
     * checkout, plus the strict key cases of S2 (a missing or swapped key).
     */
    private fun matchSeeds(): List<ByteArray> {
        val start = MatchFixtures.match(
            "Ann",
            "Bob",
            "Cid",
            legsPerSet = 2,
            sets = 3,
            colorSlots = listOf(ColorSlot.SLOT_4, ColorSlot.SLOT_1, ColorSlot.SLOT_2),
        )
        val valid = listOf(
            SavedGame(start.confirmLegsWonBy(0, 1, 1).throwing(Dart.triple(20)), isAwaitingNextPlayer = false),
            SavedGame(start.confirmLegsWonBy(2, 2), isAwaitingNextPlayer = false, isBetweenLegs = true),
            SavedGame(start.confirmLegsWonBy(0).playLegWonBy(0), isAwaitingNextPlayer = true),
        ).map { checkNotNull(gameCodec.encode(it)) }
        val body = coveredLines(valid.first())
        val keyCases = listOf(
            body.filterNot { it.startsWith("between-legs=") },
            body.filterNot { it.startsWith("colors=") },
            body.map { if (it.startsWith("legs-per-set=")) "sets=3" else if (it == "sets=3") "legs-per-set=2" else it },
        ).map(::withChecksum)
        return valid + keyCases
    }

    /**
     * Version 1 saves with real journals (R26 to R29): voided darts, several legs, a Cricket match, plus journal cases
     * that must be rejected (a missing journal line, a bad time, an undo with nothing to undo).
     */
    private fun journalSeeds(): List<ByteArray> {
        val config = SaveFixtures.config(players = 2, startScore = 301)
        val voided = SavedGame(
            X01Match(
                X01MatchConfig(config),
                emptyList(),
                X01Game(config, listOf(Dart.triple(20), Dart.single(20), Dart.double(5))),
            ),
            isAwaitingNextPlayer = true,
            journal = MatchJournal(
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
            ),
        )
        val valid = listOf(voided).map { checkNotNull(gameCodec.encode(it)) }
        val body = coveredLines(valid.first())
        val journalCases = listOf(
            body.filterNot { it.startsWith("journal=") },
            body.map { if (it.startsWith("journal=")) "journal=u" else it },
            body.map { if (it.startsWith("journal=")) it.replace("@1793520003400", "@-") else it },
            body.map { if (it.startsWith("match-id=")) "match-id=NOT-A-UUID" else it },
        ).map(::withChecksum)
        return valid + journalCases
    }

    private val validSetups: List<ByteArray> =
        (1..4).map { setupCodec.encode(SaveFixtures.setup(players = it).toLastSetups()) }

    @Test
    fun `R11 mutated saved games never crash the decoder`() = fuzz(validGames) { input ->
        when (val result = gameCodec.decode(input)) {
            // A version 1 file is canonical (R26): what is accepted re-encodes to the same bytes.
            is GameDecodeResult.Valid -> assertArrayEquals(input, gameCodec.encode(result.saved))
            is GameDecodeResult.Invalid -> Unit
        }
    }

    @Test
    fun `R11 mutated last setups never crash the decoder`() = fuzz(validSetups) { input ->
        setupCodec.decode(input)?.let { assertArrayEquals(input, setupCodec.encode(it)) }
    }

    private fun fuzz(seeds: List<ByteArray>, check: (ByteArray) -> Unit) {
        val random = Random(FUZZ_SEED)
        repeat(MUTATIONS + RANDOM_INPUTS) { iteration ->
            val input = if (iteration < MUTATIONS) mutate(seeds, random) else randomBytes(random)
            runCatching { check(input) }.onFailure {
                fail("seed $FUZZ_SEED, iteration $iteration, input ${input.toHex()}: $it")
            }
        }
    }

    private fun mutate(seeds: List<ByteArray>, random: Random): ByteArray {
        val bytes = seeds.random(random).toMutableList()
        repeat(random.nextInt(1, MAX_EDITS + 1)) {
            val at = if (bytes.isEmpty()) 0 else random.nextInt(bytes.size)
            when (random.nextInt(EDIT_KINDS)) {
                0 -> if (bytes.isNotEmpty()) bytes[at] = (bytes[at].toInt() xor (1 shl random.nextInt(8))).toByte()
                1 -> bytes.add(at, random.nextInt(256).toByte())
                2 -> if (bytes.isNotEmpty()) bytes.removeAt(at)
                3 -> if (bytes.isNotEmpty()) bytes.add(at, bytes[at])
                4 -> while (bytes.size > at) bytes.removeAt(bytes.lastIndex)
                else -> bytes.addAll(at, seeds.random(random).toList())
            }
        }
        return bytes.toByteArray()
    }

    private fun randomBytes(random: Random): ByteArray = random.nextBytes(random.nextInt(0, MAX_RANDOM_SIZE + 1))

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val FUZZ_SEED = 30L
        const val MUTATIONS = 5_000
        const val RANDOM_INPUTS = 1_000
        const val MAX_EDITS = 4
        const val EDIT_KINDS = 6
        const val MAX_RANDOM_SIZE = 70_000
    }
}
