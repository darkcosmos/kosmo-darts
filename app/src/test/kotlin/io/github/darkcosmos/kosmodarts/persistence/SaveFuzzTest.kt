package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
    ).map { checkNotNull(gameCodec.encode(it)) } + matchSeeds() + V1Fixtures.GAMES.keys.map(V1Fixtures::bytes)

    /**
     * Version 3 matches (R11, ADR 0005, ADR 0006) with non-positional color slots: in a leg, between legs and in a
     * pending checkout, plus the strict key cases of S2 (a version 1 file with a version 2 key, a version 3 file with
     * a missing or swapped key).
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
        val v1 = coveredLines(V1Fixtures.bytes("game-golden.save"))
        val keyCases = listOf(
            v1.take(5) + "legs-per-set=1" + v1.drop(5),
            body.filterNot { it.startsWith("between-legs=") },
            body.filterNot { it.startsWith("colors=") },
            body.map { if (it.startsWith("legs-per-set=")) "sets=3" else if (it == "sets=3") "legs-per-set=2" else it },
        ).map(::withChecksum)
        return valid + keyCases
    }

    private val validSetups: List<ByteArray> = (1..4).map { setupCodec.encode(SaveFixtures.setup(players = it)) }

    @Test
    fun `R11 mutated saved games never crash the decoder`() = fuzz(validGames) { input ->
        when (val result = gameCodec.decode(input)) {
            // A version 3 file is canonical; an older file re-encodes as version 3 and decodes to the same game.
            is GameDecodeResult.Valid -> if (String(input, Charsets.UTF_8).lines().getOrNull(1) != "version=3") {
                assertEquals(result, gameCodec.decode(checkNotNull(gameCodec.encode(result.saved))))
            } else {
                assertArrayEquals(input, gameCodec.encode(result.saved))
            }
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
