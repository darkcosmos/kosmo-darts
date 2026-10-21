package io.github.darkcosmos.kosmodarts.persistence

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
    ).map { checkNotNull(gameCodec.encode(it)) }

    private val validSetups: List<ByteArray> = (1..4).map { setupCodec.encode(SaveFixtures.setup(players = it)) }

    @Test
    fun `R11 mutated saved games never crash the decoder`() = fuzz(validGames) { input ->
        when (val result = gameCodec.decode(input)) {
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
