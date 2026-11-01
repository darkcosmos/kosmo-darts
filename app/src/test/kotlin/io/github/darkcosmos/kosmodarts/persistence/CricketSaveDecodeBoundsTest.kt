package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.testing.savedCricketGame
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketGame
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketLegSummary
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatchConfig
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures.match
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures.saved
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * R24 (reviews S2 and S3): every bound the decoder checks on a Cricket save, one test per bound. Each bad value gives
 * an [GameDecodeResult.Invalid] result with its reason, never an exception. The confirmed-leg summaries cannot be
 * replayed (ADR 0008), so these bounds and the match score are all that is checked about them.
 */
class CricketSaveDecodeBoundsTest {

    private val codec = GameSaveCodec()

    /** Ann and Bob, best of 2 sets of 3 legs, between their two confirmed legs (`leg=0 ...`, then `leg=1 ...`). */
    private val base = CricketSaveFixtures.BETWEEN_LEGS

    private val baseBody: List<String> = coveredLines(checkNotNull(codec.encode(base)))

    private fun assertInvalid(expected: InvalidSaveReason, lines: List<String>) {
        assertEquals(lines.toString(), GameDecodeResult.Invalid(expected), codec.decode(withChecksum(lines)))
    }

    /** The base file with its first `leg` line replaced by [value]. */
    private fun withFirstLeg(value: String): List<String> {
        val first = baseBody.indexOfFirst { it.startsWith("leg=") }
        return baseBody.mapIndexed { index, line -> if (index == first) "leg=$value" else line }
    }

    /** The base file with the value of [key] replaced. */
    private fun withValue(key: String, value: String): List<String> =
        baseBody.map { if (it.startsWith("$key=")) "$key=$value" else it }

    private fun base64(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    @Test
    fun `R24 the base file decodes`() {
        assertEquals(GameDecodeResult.Valid(base), codec.decode(withChecksum(baseBody)))
        assertTrue(baseBody.contains("leg=0 21/8/0/21 0/6/0/0"))
    }

    @Test
    fun `R24 a winner index equal to the player count or negative is out of range`() {
        assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withFirstLeg("2 21/8/0/21 0/6/0/0"))
        assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withFirstLeg("-1 21/8/0/21 0/6/0/0"))
    }

    /** M12: best of 2 sets of 3 legs, 2 players: at most (2 x 2 + 1) x (2 x 1 + 1) = 15 legs. */
    @Test
    fun `R24 a leg count above M12 is out of range`() {
        assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withValue("legs", "16"))
    }

    @Test
    fun `R24 more darts than the 10,000-dart limit in a confirmed leg are out of range`() {
        assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withFirstLeg("0 21/10001/0/21 0/6/0/0"))
        // At the limit, the leg is accepted.
        val atLimit = withFirstLeg("0 21/10000/0/21 0/6/0/0")
        assertTrue(codec.decode(withChecksum(atLimit)) is GameDecodeResult.Valid)
    }

    /** CR2: a dart gives at most 3 marks. */
    @Test
    fun `R24 more marks than 3 per dart are out of range`() {
        assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withFirstLeg("0 25/8/0/21 0/6/0/0"))
        assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withFirstLeg("0 21/8/0/21 1/0/0/0"))
    }

    /** CR8, CR12: board marks are at most the marks hit, and the winner has closed every target. */
    @Test
    fun `R24 impossible board marks are out of range`() {
        assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withFirstLeg("0 21/8/0/20 0/6/0/0"))
        assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withFirstLeg("0 21/8/0/21 3/6/0/4"))
        assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withFirstLeg("0 24/8/0/22 0/6/0/0"))
    }

    @Test
    fun `R24 points above 99,999 or signed are out of range`() {
        listOf("100000", "-5", "+5", "05").forEach {
            assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withFirstLeg("0 21/8/$it/21 0/6/0/0"))
        }
        val atLimit = withFirstLeg("0 21/8/99999/21 0/6/0/0")
        assertTrue(codec.decode(withChecksum(atLimit)) is GameDecodeResult.Valid)
    }

    @Test
    fun `R24 a leg value with a wrong number of players or figures is malformed`() {
        listOf(
            "0 21/8/0/21",
            "0 21/8/0/21 0/6/0/0 0/6/0/0",
            "0 21/8/0 0/6/0/0",
            "0 21/8/0/21/0 0/6/0/0",
            "0  21/8/0/21 0/6/0/0",
        ).forEach { assertInvalid(InvalidSaveReason.MALFORMED, withFirstLeg(it)) }
    }

    /** R12, R24: a confirmed leg over 99,999 points is not written, so the previous file is kept. */
    @Test
    fun `R24 a confirmed leg over 99,999 points is not encoded`() {
        assertNull(codec.encode(withConfirmedLegPoints(SaveFormat.MAX_INTEGER + 1)))
        val atLimit = withConfirmedLegPoints(SaveFormat.MAX_INTEGER)
        assertEquals(GameDecodeResult.Valid(atLimit), codec.decode(checkNotNull(codec.encode(atLimit))))
    }

    private fun withConfirmedLegPoints(points: Int): SavedCricketGame {
        val config = CricketConfig(listOf("Ann", "Bob"))
        val leg = CricketLegSummary(config, 0, listOf(21, 0), listOf(8, 6), listOf(points, 0), listOf(21, 0))
        val match = CricketMatch(
            CricketMatchConfig(config, MatchFormat(legsPerSet = 2)),
            listOf(leg),
            CricketGame(config, startingPlayerIndex = 1),
        )
        return savedCricketGame(match, isAwaitingNextPlayer = false, isBetweenLegs = true)
    }

    /** M4: the confirmed legs never decide the match; the deciding leg stays current. */
    @Test
    fun `R24 a confirmed leg after the match is decided is rejected by the engine`() {
        val decided = saved(match("Ann", "Bob", legsPerSet = 2).confirmLegsWonBy(0))
        val body = coveredLines(checkNotNull(codec.encode(decided)))
        val leg = body.single { it.startsWith("leg=") }
        val twoLegs = body.flatMap {
            when {
                it == leg -> listOf(leg, leg.replaceFirst("leg=0 21/8/0/21 0/6/0/0", "leg=0 21/8/0/21 0/7/0/0"))
                it == "legs=1" -> listOf("legs=2")
                else -> listOf(it)
            }
        }

        assertInvalid(InvalidSaveReason.REJECTED_BY_ENGINE, JournalFixtures.withJournalCount(twoLegs, count = 3))
    }

    @Test
    fun `R24 zero or five players are out of range`() {
        listOf("0", "5").forEach { assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withValue("players", it)) }
    }

    /** PlayerListRules (C4): the InvalidCricketConfigException is caught and the file is rejected. */
    @Test
    fun `R24 a duplicate name or a blank name is an invalid configuration`() {
        val bob = "player=${base64("Bob")}"
        val duplicate = baseBody.map { if (it == bob) "player=${base64("ANN")}" else it }
        assertInvalid(InvalidSaveReason.INVALID_CONFIG, duplicate)
        assertInvalid(InvalidSaveReason.INVALID_CONFIG, baseBody.map { if (it == bob) "player=" else it })
    }

    @Test
    fun `R24 unknown variant and 25-Bull tokens are out of range`() {
        listOf("cutthroat", "Standard", "x01", "").forEach {
            assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withValue("variant", it))
        }
        listOf("2", "true", "01", "").forEach { assertInvalid(InvalidSaveReason.OUT_OF_RANGE, withValue("bull", it)) }
    }

    /**
     * ADR 0008, "Size limit": the largest undecided match (4 players, best of 9 sets of 9 legs: 1,088 confirmed legs)
     * with the largest figures, 10,000 triples in the current leg and long journals is over
     * [GameSaveCodec.MAX_FILE_BYTES], so it is not written; with misses in the current leg and the plain journal, it
     * fits, and it round trips.
     */
    @Test
    fun `R12 a Cricket save over the file size limit is not encoded`() {
        val bloated = largestMatch(Dart.triple(20))
        // 14,000 voided darts in each of the 4 first legs, 20 bytes each: over 1 MiB together (R28).
        val padded = JournalFixtures.withVoided(bloated.journal, legs = 4, pairs = 14_000)
        assertNull(codec.encode(bloated.copy(journal = padded)))
        val fitting = largestMatch(Dart.MISS)
        val bytes = checkNotNull(codec.encode(fitting))

        assertTrue(bytes.size <= GameSaveCodec.MAX_FILE_BYTES)
        assertEquals(GameDecodeResult.Valid(fitting), codec.decode(bytes))
    }

    private fun largestMatch(dart: Dart): SavedCricketGame {
        val config = CricketConfig(listOf("Ann", "Bob", "Cid", "Dan"))
        val players = config.players.indices
        val fullBoard = 21
        val darts = GameSaveCodec.MAX_DARTS
        fun leg(winner: Int) = CricketLegSummary(
            config = config,
            winnerIndex = winner,
            marksHit = players.map { 3 * darts },
            dartsThrown = players.map { darts },
            points = players.map { SaveFormat.MAX_INTEGER },
            boardMarks = players.map { fullBoard },
        )
        // 32 sets of 33 legs: 8 legs each, then the set winner's ninth; each player wins 8 sets. Then 32 legs of the
        // deciding set, 8 each: 1,088 legs, and the match is still open.
        val rotation = List(32) { it % 4 }
        val winners = List(32) { set -> rotation + set % 4 }.flatten() + rotation
        val legs = winners.map(::leg)
        val current = CricketGame(config, List(darts) { dart }, startingPlayerIndex = legs.size % 4)
        val match = CricketMatch(CricketMatchConfig(config, MatchFormat(legsPerSet = 9, sets = 9)), legs, current)
        return savedCricketGame(match, isAwaitingNextPlayer = false)
    }
}
