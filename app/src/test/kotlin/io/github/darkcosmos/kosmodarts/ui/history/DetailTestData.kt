package io.github.darkcosmos.kosmodarts.ui.history

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedDart
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedLeg
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.journal.DartSource
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.history.MatchDetail

/** A stored dart for the detail tests: thrown by player [player] in visit [visit] of leg [leg]. */
fun detailDart(
    leg: Int,
    player: Int,
    visit: Int,
    rank: Int,
    hit: Dart,
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
    enteredAt = NOON_MILLIS + visit * 1_000L + rank,
    point = null,
)

/** The legs of a match of [sets] sets of [legsPerSet] legs, the players taking turns to start and to win. */
fun legsOf(sets: Int, legsPerSet: Int, players: Int): List<ArchivedLeg> =
    (0 until sets * legsPerSet).map { ordinal ->
        ArchivedLeg(
            ordinal = ordinal,
            setNumber = ordinal / legsPerSet + 1,
            legNumberInSet = ordinal % legsPerSet + 1,
            starterPosition = ordinal % players,
            winnerPosition = (ordinal + 1) % players,
        )
    }

/** A bust, a 180, a double-out finish, in each leg: enough darts for a screenshot of the whole detail. */
fun x01Darts(legs: Int): List<ArchivedDart> = (0 until legs).flatMap { leg ->
    listOf(
        detailDart(leg, 0, 1, 1, Dart(20, Multiplier.TRIPLE)),
        detailDart(leg, 0, 1, 2, Dart(20, Multiplier.TRIPLE)),
        detailDart(leg, 0, 1, 3, Dart(20, Multiplier.TRIPLE)),
        detailDart(leg, 1, 2, 1, Dart.BULL),
        detailDart(leg, 1, 2, 2, Dart.MISS),
        detailDart(leg, 1, 2, 3, Dart(19, Multiplier.SINGLE), isBust = true),
        detailDart(leg, 0, 3, 1, Dart(16, Multiplier.DOUBLE)),
    )
}

/** An X01 match of [sets] sets of [legsPerSet] legs, ended [minutes] minutes after it started. */
fun x01Detail(
    names: List<String> = listOf("Ana", "Bob"),
    sets: Int = 2,
    legsPerSet: Int = 3,
    minutes: Long = 47,
    sessionId: Long = 7,
): MatchDetail {
    val format = MatchFormat(legsPerSet = legsPerSet, sets = sets)
    val legs = legsOf(sets, legsPerSet, names.size)
    return MatchDetail(
        sessionId = sessionId,
        startedAt = NOON_MILLIS,
        endedAt = NOON_MILLIS + minutes * 60_000L,
        settings = ArchivedSettings.X01(501, CheckoutRule.DOUBLE_OUT, format),
        players = historyPlayers(*names.toTypedArray(), legs = names.map { 3 }, sets = names.map { 1 }),
        legs = legs,
        darts = x01Darts(legs.size),
    )
}

/** A Cricket match of [names] players, one leg, ended 25 minutes after it started. */
fun cricketDetail(
    names: List<String> = listOf("Ana", "Bob", "Cleo", "Dan"),
    isBullInPlay: Boolean = true,
    sessionId: Long = 8,
): MatchDetail {
    val darts = names.indices.flatMap { player ->
        listOf(
            detailDart(0, player, player + 1, 1, Dart(20, Multiplier.TRIPLE)),
            detailDart(0, player, player + 1, 2, Dart(19, Multiplier.DOUBLE)),
            detailDart(0, player, player + 1, 3, Dart.BULL),
        )
    }
    return MatchDetail(
        sessionId = sessionId,
        startedAt = NOON_MILLIS,
        endedAt = NOON_MILLIS + 25 * 60_000L,
        settings = ArchivedSettings.Cricket(CricketVariant.CUT_THROAT, isBullInPlay, MatchFormat()),
        players = historyPlayers(*names.toTypedArray()),
        legs = legsOf(1, 1, names.size),
        darts = darts,
    )
}
