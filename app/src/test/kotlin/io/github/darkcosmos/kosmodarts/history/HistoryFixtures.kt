package io.github.darkcosmos.kosmodarts.history

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedDart
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedLeg
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMatch
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedPlayer
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.archive.ENGINE_RULES_VERSION
import io.github.darkcosmos.kosmodarts.engine.archive.SessionType
import io.github.darkcosmos.kosmodarts.engine.board.BoardPoint
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.journal.Clock
import io.github.darkcosmos.kosmodarts.engine.journal.DartSource
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import java.util.Locale
import java.util.concurrent.Executor

/** Runs every Room task on the calling thread: no background thread, no idling wait (review T5 minor). */
val directExecutor: Executor = Executor { it.run() }

/** Name key of the tests: trimmed and lower-cased (the app passes the setup's key, plan D12). */
val testNameKey: (String) -> String = { it.trim().lowercase(Locale.ROOT) }

const val APP_VERSION = "0.1.0"
const val PLAYER_1_ARGB: Int = 0xFF2196F3.toInt()
const val PLAYER_2_ARGB: Int = 0xFFFF9800.toInt()

/** A fixed clock that moves one second forward on each read: deterministic times, no sleep. */
class FakeClock(private var now: Long = 1_750_000_000_000L) : Clock {
    override fun nowMillis(): Long = now.also { now += 1_000L }
}

/** An in-memory database built like the app's (same corruption handler), on the calling thread. */
fun inMemoryHistoryDatabase(context: Context): HistoryDatabase =
    Room.inMemoryDatabaseBuilder(context, HistoryDatabase::class.java)
        .keepingCorruptFile()
        .onCallingThread()
        .build()

/** Lets Robolectric's main-thread tests run Room on the calling thread (tests only). */
fun RoomDatabase.Builder<HistoryDatabase>.onCallingThread(): RoomDatabase.Builder<HistoryDatabase> =
    allowMainThreadQueries().setQueryExecutor(directExecutor).setTransactionExecutor(directExecutor)

private fun dart(
    clock: Clock,
    leg: Int,
    player: Int,
    visit: Int,
    rank: Int,
    hit: Dart,
    point: BoardPoint?,
    isBust: Boolean = false,
    isVoided: Boolean = false,
) = ArchivedDart(
    legOrdinal = leg,
    playerPosition = player,
    visit = visit,
    rank = rank,
    segment = hit.segment,
    multiplier = hit.multiplier,
    isBust = isBust,
    isVoided = isVoided,
    source = DartSource.MANUAL,
    enteredAt = clock.nowMillis(),
    point = point,
)

val T20_POINT = BoardPoint(xMm = 0.0, yMm = 103.0)
val D16_POINT = BoardPoint(xMm = -51.94, yMm = -159.85)

/**
 * A 501 double-out match, first to 2 legs, between [names]: two legs won by the first player, with a hit, a miss
 * (no point), a bull, a voided dart, a bust and a hit without a known position.
 */
fun x01Match(
    matchId: String = "11111111-1111-4111-8111-111111111111",
    names: List<String> = listOf("Ana", "Bob"),
    clock: Clock = FakeClock(),
): ArchivedMatch {
    val startedAt = clock.nowMillis()
    val darts = listOf(
        dart(clock, leg = 0, player = 0, visit = 1, rank = 1, hit = Dart(20, Multiplier.TRIPLE), point = T20_POINT),
        dart(clock, leg = 0, player = 0, visit = 1, rank = 2, hit = Dart.MISS, point = null),
        dart(clock, leg = 0, player = 0, visit = 1, rank = 3, hit = Dart.BULL, point = BoardPoint(0.0, 0.0)),
        dart(clock, leg = 0, player = 1, visit = 2, rank = 1, hit = Dart(5), point = null, isVoided = true),
        dart(clock, leg = 0, player = 1, visit = 2, rank = 1, hit = Dart(16, Multiplier.DOUBLE), point = D16_POINT),
        dart(clock, leg = 1, player = 1, visit = 1, rank = 1, hit = Dart.OUTER_BULL, point = null, isBust = true),
        dart(clock, leg = 1, player = 0, visit = 2, rank = 1, hit = Dart(20, Multiplier.TRIPLE), point = T20_POINT),
    )
    return ArchivedMatch(
        matchId = matchId,
        sessionType = SessionType.MATCH,
        startedAt = startedAt,
        endedAt = clock.nowMillis(),
        rulesVersion = ENGINE_RULES_VERSION,
        settings = ArchivedSettings.X01(501, CheckoutRule.DOUBLE_OUT, MatchFormat(legsPerSet = 2, sets = 1)),
        players = names.mapIndexed { position, name ->
            ArchivedPlayer(
                position = position,
                name = name,
                colorSlot = ColorSlot.entries[position],
                rank = position + 1,
                legsWon = if (position == 0) 2 else 0,
                setsWon = if (position == 0) 1 else 0,
            )
        },
        legs = listOf(
            ArchivedLeg(ordinal = 0, setNumber = 1, legNumberInSet = 1, starterPosition = 0, winnerPosition = 0),
            ArchivedLeg(ordinal = 1, setNumber = 1, legNumberInSet = 2, starterPosition = 1, winnerPosition = 0),
        ),
        darts = darts,
    )
}

/** A cut-throat Cricket single game without the bull, won by the second player. */
fun cricketMatch(
    matchId: String = "22222222-2222-4222-8222-222222222222",
    names: List<String> = listOf("Ana", "Cleo"),
    clock: Clock = FakeClock(),
): ArchivedMatch {
    val startedAt = clock.nowMillis()
    val darts = listOf(
        dart(clock, leg = 0, player = 0, visit = 1, rank = 1, hit = Dart(20, Multiplier.TRIPLE), point = T20_POINT),
        dart(clock, leg = 0, player = 1, visit = 2, rank = 1, hit = Dart(16, Multiplier.DOUBLE), point = D16_POINT),
        dart(clock, leg = 0, player = 1, visit = 2, rank = 2, hit = Dart.MISS, point = null, isVoided = true),
    )
    return ArchivedMatch(
        matchId = matchId,
        sessionType = SessionType.MATCH,
        startedAt = startedAt,
        endedAt = clock.nowMillis(),
        rulesVersion = ENGINE_RULES_VERSION,
        settings = ArchivedSettings.Cricket(CricketVariant.CUT_THROAT, isBullInPlay = false, format = MatchFormat()),
        players = names.mapIndexed { position, name ->
            ArchivedPlayer(
                position = position,
                name = name,
                colorSlot = ColorSlot.entries[position],
                rank = if (position == 1) 1 else 2,
                legsWon = if (position == 1) 1 else 0,
                setsWon = if (position == 1) 1 else 0,
            )
        },
        legs = listOf(
            ArchivedLeg(ordinal = 0, setNumber = 1, legNumberInSet = 1, starterPosition = 0, winnerPosition = 1),
        ),
        darts = darts,
    )
}

/** One color per player of [match]. */
fun colorsOf(match: ArchivedMatch): List<Int> =
    match.players.map { if (it.position % 2 == 0) PLAYER_1_ARGB else PLAYER_2_ARGB }
