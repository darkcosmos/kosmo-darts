package io.github.darkcosmos.kosmodarts.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedDart
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedLeg
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMatch
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedPlayer
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.archive.ENGINE_RULES_VERSION
import io.github.darkcosmos.kosmodarts.engine.archive.SessionType
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.journal.DartSource
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor
import io.github.darkcosmos.kosmodarts.engine.profile.ProfileRules
import io.github.darkcosmos.kosmodarts.engine.stats.StatsMatch
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The profile data loader (AR5, AR6, AR9, ST7, ST10, PM7, HS7). */
@RunWith(RobolectricTestRunner::class)
class ProfileStatsRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: HistoryDatabase
    private lateinit var archive: ArchiveRepository
    private lateinit var repository: RoomProfileStatsRepository
    private var matchNumber = 0

    @Before
    fun open() {
        db = inMemoryHistoryDatabase(context)
        archive = ArchiveRepository(db, testNameKey)
        repository = RoomProfileStatsRepository(db)
    }

    @After
    fun close() = db.close()

    private fun big() = List(3) { Dart.triple(20) }

    private fun x01Visits(): List<List<Dart>> =
        listOf(big(), listOf(Dart.triple(20), Dart.triple(19), Dart.double(2)))

    private fun cricketVisits(): List<List<Dart>> = listOf(
        listOf(Dart.triple(20), Dart.triple(19), Dart.triple(18)),
        listOf(Dart.triple(17), Dart.triple(16), Dart.triple(15)),
        listOf(Dart.BULL, Dart.OUTER_BULL),
    )

    private fun archivedDart(leg: Int, position: Int, visit: Int, rank: Int, dart: Dart, isVoided: Boolean = false) =
        ArchivedDart(
            legOrdinal = leg,
            playerPosition = position,
            visit = visit,
            rank = rank,
            segment = dart.segment,
            multiplier = dart.multiplier,
            isBust = false,
            isVoided = isVoided,
            source = DartSource.MANUAL,
            enteredAt = 1L,
            point = null,
        )

    /** The darts of one leg: [visits] of player 0, three misses of each other player between them, one voided dart. */
    private fun legDarts(leg: Int, players: Int, visits: List<List<Dart>>): List<ArchivedDart> {
        val result = ArrayList<ArchivedDart>()
        var visit = 0
        visits.forEachIndexed { index, darts ->
            visit += 1
            darts.forEachIndexed { rank, dart -> result += archivedDart(leg, 0, visit, rank + 1, dart) }
            if (index < visits.lastIndex) {
                for (other in 1 until players) {
                    visit += 1
                    result += archivedDart(leg, other, visit, 1, Dart(5), isVoided = true)
                    repeat(3) { rank -> result += archivedDart(leg, other, visit, rank + 1, Dart.MISS) }
                }
            }
        }
        return result
    }

    private fun match(
        names: List<String>,
        settings: ArchivedSettings,
        visits: List<List<Dart>>,
        legs: Int = 1,
        endedAt: Long = 2_000L,
    ): ArchivedMatch {
        matchNumber += 1
        return ArchivedMatch(
            matchId = "00000000-0000-4000-8000-%012d".format(matchNumber),
            sessionType = SessionType.MATCH,
            startedAt = endedAt - 1_000L,
            endedAt = endedAt,
            rulesVersion = ENGINE_RULES_VERSION,
            settings = settings,
            players = names.mapIndexed { position, name ->
                ArchivedPlayer(
                    position = position,
                    name = name,
                    color = PlayerColor.PALETTE[position],
                    rank = if (position == 0) 1 else 2,
                    legsWon = if (position == 0) legs else 0,
                    setsWon = if (position == 0) 1 else 0,
                )
            },
            legs = List(legs) {
                ArchivedLeg(it, setNumber = 1, legNumberInSet = it + 1, starterPosition = 0, winnerPosition = 0)
            },
            darts = (0 until legs).flatMap { legDarts(it, names.size, visits) },
        )
    }

    private fun x01(names: List<String>, legs: Int = 1, endedAt: Long = 2_000L) = match(
        names,
        ArchivedSettings.X01(301, CheckoutRule.DOUBLE_OUT, MatchFormat(legsPerSet = legs, sets = 1)),
        x01Visits(),
        legs,
        endedAt,
    )

    private fun cricket(names: List<String>, endedAt: Long = 2_000L) = match(
        names,
        ArchivedSettings.Cricket(CricketVariant.STANDARD, isBullInPlay = true, format = MatchFormat()),
        cricketVisits(),
        endedAt = endedAt,
    )

    private fun store(match: ArchivedMatch) {
        archive.archive(match, colorsOf(match), APP_VERSION)
    }

    private fun idOf(name: String): Long = checkNotNull(db.profileDao().findByNameKey(testNameKey(name))).id

    private fun load(name: String = "Ana"): ProfileStatsData = (repository.load(idOf(name)) as DbResult.Ok).value

    private fun sql(statement: String) = db.openHelper.writableDatabase.execSQL(statement)

    /** What the statistics read of a match, without the session id (the row id changes with insertion order). */
    private fun figures(match: StatsMatch) =
        listOf(match.startedAt, match.endedAt, match.settings, match.players, match.legs)

    @Test
    fun `ST7 AR5 a profile in solo, 2, 3 and 4 player matches gets every match with every player's darts`() {
        store(x01(listOf("Ana"), endedAt = 1_000L))
        store(x01(listOf("Ana", "Bob"), endedAt = 2_000L))
        store(cricket(listOf("Ana", "Bob", "Cleo"), endedAt = 3_000L))
        store(x01(listOf("Ana", "Bob", "Cleo", "Dan"), legs = 2, endedAt = 4_000L))
        store(x01(listOf("Bob", "Cleo"), endedAt = 5_000L))

        val data = load()

        assertEquals(0, data.skippedMatches)
        assertEquals(listOf(1, 2, 3, 4), data.matches.map { it.players.size })
        assertEquals(listOf(1_000L, 2_000L, 3_000L, 4_000L), data.matches.map { it.endedAt })
        assertEquals("Ana", data.name)
        // Every player's darts are loaded; the voided ones are left to the engine (DJ2).
        val four = data.matches[3]
        assertEquals(2, four.legs.size)
        assertEquals(listOf(0, 1, 2, 3), four.legs[0].darts.map { it.position }.distinct())
        assertEquals(6 + 9, four.legs[0].darts.size)
    }

    @Test
    fun `AR6 a stored bull and a miss reach the engine as the bull and the miss`() {
        store(cricket(listOf("Ana", "Bob")))

        val darts = load().matches.single().legs.single().darts.map { it.dart }

        assertTrue(Dart.BULL in darts)
        assertTrue(Dart.OUTER_BULL in darts)
        assertTrue(Dart.MISS in darts)
    }

    @Test
    fun `a profile with no match gets an empty list`() {
        db.profileDao().upsertUsed("Zed", "zed", at = 1L)

        val data = load("Zed")

        assertTrue(data.matches.isEmpty())
        assertEquals(0, data.skippedMatches)
    }

    @Test
    fun `an unknown profile id gives Failed, not a crash`() {
        assertEquals(DbResult.Failed, repository.load(404L))
    }

    @Test
    fun `PM7 the anonymous profile is never loaded`() {
        store(x01(listOf("Ana", "Bob")))
        val anonymous = db.profileDao().upsertUsed("Deleted", ProfileRules.ANONYMOUS_NAME_KEY, at = 1L)

        assertEquals(DbResult.Failed, repository.load(anonymous))
    }

    @Test
    fun `AR9 HS7 a read failure gives Failed, not a skipped count`() {
        store(x01(listOf("Ana", "Bob")))
        val id = idOf("Ana")
        db.close()

        assertEquals(DbResult.Failed, repository.load(id))
    }

    @Test
    fun `ST10 corrupt matches are counted and skipped, the valid ones load intact`() {
        store(x01(listOf("Ana", "Bob"), endedAt = 1_000L))
        store(cricket(listOf("Ana", "Cleo"), endedAt = 5_000L))
        val baseline = load().matches.map(::figures)

        val firstDartAfterWin = x01(listOf("Ana", "Bob"), endedAt = 2_000L).let {
            it.copy(darts = it.darts + archivedDart(0, 0, 9, 1, Dart(1)))
        }
        val legWithoutDarts = x01(listOf("Ana", "Bob"), endedAt = 3_000L).let { m ->
            m.copy(darts = m.darts.filterNot { it.legOrdinal == 0 })
        }
        val rankOutOfRange = x01(listOf("Ana", "Bob"), endedAt = 4_000L).let { m ->
            m.copy(players = m.players.map { if (it.position == 1) it.copy(rank = 9) else it })
        }
        listOf(firstDartAfterWin, legWithoutDarts, rankOutOfRange).forEach(::store)
        val unknownDart = x01(listOf("Ana", "Bob"), endedAt = 6_000L).also(::store)
        sql("UPDATE dart SET multiplier = 7 WHERE leg_id IN (SELECT l.id FROM leg l JOIN match_record m " +
            "ON m.match_uuid = '${unknownDart.matchId}' AND l.session_id = m.session_id) AND rank = 1 AND visit = 1")
        val badSettings = x01(listOf("Ana", "Bob"), endedAt = 7_000L).also(::store)
        sql("UPDATE match_record SET settings = 'garbage' WHERE match_uuid = '${badSettings.matchId}'")

        val data = load()

        assertEquals(5, data.skippedMatches)
        assertEquals(baseline, data.matches.map(::figures))
    }

    @Test
    fun `AR5 more than 100 sessions load in two batches, each match once and in order`() {
        val total = 130
        repeat(total) { store(x01(listOf("Ana", "Bob"), endedAt = 1_000L + it)) }

        val data = load()

        assertEquals(0, data.skippedMatches)
        assertEquals(total, data.matches.size)
        assertEquals((0 until total).map { 1_000L + it }, data.matches.map { it.endedAt })
        assertEquals(total, data.matches.map { it.sessionId }.distinct().size)
    }

    @Test
    fun `ST10 loading 1000 matches loads each once and in order`() {
        repeat(1_000) { store(x01(listOf("Ana", "Bob"), endedAt = 1_000L + it)) }
        val id = idOf("Ana")

        val data = (repository.load(id) as DbResult.Ok).value

        assertEquals(0, data.skippedMatches)
        assertEquals(1_000, data.matches.size)
        assertEquals((0 until 1_000).map { 1_000L + it }, data.matches.map { it.endedAt })
    }

    @Test
    fun `ST10 AR9 an unreadable mode column gives Failed, not a crash`() {
        val bad = x01(listOf("Ana", "Bob"), endedAt = 1_000L).also(::store)
        sql("UPDATE match_record SET mode = 'GARBAGE' WHERE match_uuid = '${bad.matchId}'")

        assertEquals(DbResult.Failed, repository.load(idOf("Ana")))
    }

    private fun picker(name: String = "Ana"): HeadToHeadPicker =
        (repository.loadPicker(idOf(name)) as DbResult.Ok).value

    private fun pair(a: String = "Ana", b: String = "Bob"): HeadToHeadData =
        (repository.loadPair(idOf(a), idOf(b)) as DbResult.Ok).value

    @Test
    fun `HH1 the picker lists others but self and anonymous, by common matches then name, zero included`() {
        store(x01(listOf("Ana", "Bob"), endedAt = 1_000L))
        store(cricket(listOf("Ana", "Bob", "Cleo"), endedAt = 2_000L))
        store(x01(listOf("Ana", "Bob"), endedAt = 3_000L))
        store(x01(listOf("Ana", "Zoe"), endedAt = 4_000L))
        store(x01(listOf("Bob", "Cleo"), endedAt = 5_000L))
        db.profileDao().upsertUsed("Dan", "dan", at = 1L)
        db.profileDao().upsertUsed("Abe", "abe", at = 1L)
        db.profileDao().upsertUsed("Deleted", ProfileRules.ANONYMOUS_NAME_KEY, at = 1L)

        val picker = picker()

        assertEquals("Ana", picker.name)
        assertEquals(
            listOf("Bob" to 3, "Cleo" to 1, "Zoe" to 1, "Abe" to 0, "Dan" to 0),
            picker.others.map { it.name to it.commonCount },
        )
    }

    @Test
    fun `HH1 HH2 a profile really reset after the common matches counts none`() {
        store(x01(listOf("Ana", "Bob"), endedAt = 1_000L))
        store(x01(listOf("Ana", "Bob"), endedAt = 9_000L))
        sql("UPDATE profile SET reset_at = 5000 WHERE id = ${idOf("Bob")}")

        assertEquals(1, picker().others.single().commonCount)
    }

    @Test
    fun `HH1 AR9 HS7 the picker of an unknown or anonymous profile, or after a read failure, is Failed`() {
        store(x01(listOf("Ana", "Bob")))
        val anonymous = db.profileDao().upsertUsed("Deleted", ProfileRules.ANONYMOUS_NAME_KEY, at = 1L)
        val id = idOf("Ana")

        assertEquals(DbResult.Failed, repository.loadPicker(404L))
        assertEquals(DbResult.Failed, repository.loadPicker(anonymous))
        db.close()
        assertEquals(DbResult.Failed, repository.loadPicker(id))
    }

    @Test
    fun `HH2 the pair gets the matches both played with every player's darts, oldest end first`() {
        store(x01(listOf("Ana", "Bob"), endedAt = 3_000L))
        store(x01(listOf("Ana"), endedAt = 1_000L))
        store(cricket(listOf("Ana", "Bob", "Cleo"), endedAt = 2_000L))
        store(x01(listOf("Ana", "Cleo"), endedAt = 4_000L))
        store(x01(listOf("Bob", "Cleo"), endedAt = 5_000L))

        val data = pair()

        assertEquals(0, data.skippedMatches)
        assertEquals(listOf(2_000L, 3_000L), data.matches.map { it.endedAt })
        assertEquals(listOf("Ana", "Bob"), listOf(data.a.name, data.b.name))
        assertEquals(listOf(idOf("Ana"), idOf("Bob")), listOf(data.a.id, data.b.id))
        assertEquals(listOf(3, 2), data.matches.map { it.players.size })
        assertTrue(data.matches.all { it.legs.single().darts.isNotEmpty() })
    }

    @Test
    fun `HH9 ST10 a corrupt common match is skipped and counted, the others load intact`() {
        store(x01(listOf("Ana", "Bob"), endedAt = 1_000L))
        val baseline = pair().matches.map(::figures)
        val rankOutOfRange = x01(listOf("Ana", "Bob"), endedAt = 2_000L).let { m ->
            m.copy(players = m.players.map { if (it.position == 1) it.copy(rank = 9) else it })
        }
        store(rankOutOfRange)
        // A corrupt match Bob did not play is not theirs to count.
        store(
            x01(listOf("Ana", "Cleo"), endedAt = 3_000L).let { m ->
                m.copy(players = m.players.map { if (it.position == 1) it.copy(rank = 9) else it })
            },
        )

        val data = pair()

        assertEquals(1, data.skippedMatches)
        assertEquals(baseline, data.matches.map(::figures))
    }

    @Test
    fun `HH2 AR5 more than 100 common sessions load in two batches, each match once and in order`() {
        repeat(130) { store(x01(listOf("Ana", "Bob"), endedAt = 1_000L + it)) }

        val data = pair()

        assertEquals((0 until 130).map { 1_000L + it }, data.matches.map { it.endedAt })
    }

    @Test
    fun `HH9 AR9 a pair with itself, an unknown or anonymous profile, or after a read failure, is Failed`() {
        store(x01(listOf("Ana", "Bob")))
        val ana = idOf("Ana")
        val bob = idOf("Bob")
        val anonymous = db.profileDao().upsertUsed("Deleted", ProfileRules.ANONYMOUS_NAME_KEY, at = 1L)

        assertEquals(DbResult.Failed, repository.loadPair(ana, ana))
        assertEquals(DbResult.Failed, repository.loadPair(ana, 404L))
        assertEquals(DbResult.Failed, repository.loadPair(anonymous, bob))
        db.close()
        assertEquals(DbResult.Failed, repository.loadPair(ana, bob))
    }
}
