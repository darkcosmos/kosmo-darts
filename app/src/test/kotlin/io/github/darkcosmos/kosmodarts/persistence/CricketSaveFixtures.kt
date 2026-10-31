package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatchConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketTarget
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/** Cricket matches used by the persistence tests (docs/rules/cricket.md, docs/rules/match.md). Names are made up. */
internal object CricketSaveFixtures {

    /** A new Cricket match: standard, 25-Bull in play and a single game by default. */
    fun match(
        vararg players: String,
        variant: CricketVariant = CricketVariant.STANDARD,
        isBullInPlay: Boolean = true,
        legsPerSet: Int = 1,
        sets: Int = 1,
        colorSlots: List<ColorSlot> = ColorSlot.byPosition(players.size),
    ): CricketMatch = CricketMatch.start(
        CricketMatchConfig(
            CricketConfig(players.toList(), variant, isBullInPlay),
            MatchFormat(legsPerSet, sets),
            colorSlots,
        ),
    )

    /**
     * The saved game of [match]: it waits only if its last dart completed a turn or won the leg, read from the
     * engine's completed turns, independently of [SavedCricketGame.isConsistent], which the tests check.
     */
    fun saved(match: CricketMatch, isBetweenLegs: Boolean = false): SavedCricketGame {
        val state = match.currentLeg.state
        val darts = match.currentLeg.darts.size
        val inCompletedTurns = state.completedTurns.sumOf { it.darts.size }
        val waits = state.isFinished || (darts > 0 && inCompletedTurns == darts)
        return SavedCricketGame(match, isAwaitingNextPlayer = waits, isBetweenLegs = isBetweenLegs)
    }

    /** Ann and Bob, a single standard game: Ann has thrown T20 and 19 of her first turn. */
    val MID_TURN: SavedCricketGame = saved(match("Ann", "Bob").throwing(Dart.triple(20), Dart.single(19)))

    /** Ann's T20 T19 T18 waits for "Next player". */
    val WAITING: SavedCricketGame =
        saved(match("Ann", "Bob").throwing(Dart.triple(20), Dart.triple(19), Dart.triple(18)))

    /** A best of two sets of two legs, three players with non-positional slots, one leg confirmed and a dart thrown. */
    val MATCH_MID_LEG: SavedCricketGame = saved(
        match(
            "Ann",
            "Bob",
            "Cid",
            legsPerSet = 2,
            sets = 2,
            colorSlots = listOf(ColorSlot.SLOT_3, ColorSlot.SLOT_1, ColorSlot.SLOT_4),
        ).confirmLegsWonBy(1).throwing(Dart.triple(20)),
    )

    /** Two confirmed legs, shown between legs. */
    val BETWEEN_LEGS: SavedCricketGame =
        saved(match("Ann", "Bob", legsPerSet = 3, sets = 2).confirmLegsWonBy(0, 1), isBetweenLegs = true)

    /** Ann's win of the deciding leg, waiting in the VICTOIRE zone (CR25). */
    val PENDING_MATCH_WIN: SavedCricketGame =
        saved(match("Ann", "Bob", legsPerSet = 2).confirmLegsWonBy(0).playLegWonBy(0))

    /**
     * Cut-throat, Ann, Bob and Cid (the engine's CR9 case): Ann closes everything with the fewest points, and Cid's
     * T19, which gives Bob 57 more points, makes Ann win while Cid throws (CR9, CR25).
     */
    val NON_THROWER_WIN: SavedCricketGame = saved(
        match("Ann", "Bob", "Cid", variant = CricketVariant.CUT_THROAT).throwing(
            *darts(
                "0 0 0", "T20 T20 0", "0 0 0",
                "T20 T19 T18", "0 0 0", "T19 T19 0",
                "T17 T16 T15", "0 0 0", "0 0 0",
                "50 25 0", "0 0 0", "T19",
            ),
        ),
    )

    /** The darts of the save tokens of [turns], one string per turn. */
    fun darts(vararg turns: String): Array<Dart> =
        turns.flatMap { it.split(' ') }.map { checkNotNull(DartTokens.dartOf(it)) { it } }.toTypedArray()
}

/** Throws each dart, in order. */
internal fun CricketMatch.throwing(vararg darts: Dart): CricketMatch =
    darts.fold(this) { match, dart -> match.throwDart(dart) }

/**
 * Plays the current leg until [winner] wins it, without confirming it: [winner] closes each target in play in board
 * order without a surplus mark (a triple per number, then Bull and 25), every other player misses. Nobody scores.
 */
internal fun CricketMatch.playLegWonBy(winner: Int): CricketMatch {
    val closingDarts = config.targets.flatMap(::closingDarts)
    var match = this
    while (!match.currentLeg.state.isFinished) {
        val state = match.currentLeg.state
        val player = checkNotNull(state.currentPlayerIndex)
        val dart = if (player == winner) closingDarts[state.players[player].dartsThrown] else Dart.MISS
        match = match.throwDart(dart)
    }
    return match
}

/** Plays and confirms one leg per winner, in order. */
internal fun CricketMatch.confirmLegsWonBy(vararg winners: Int): CricketMatch =
    winners.fold(this) { match, winner -> match.playLegWonBy(winner).confirmLeg() }

private fun closingDarts(target: CricketTarget): List<Dart> =
    if (target.isNumber) listOf(Dart.triple(target.points)) else listOf(Dart.BULL, Dart.OUTER_BULL)

/** The Cricket game of a valid result; fails the test if the file held another mode. */
internal val GameDecodeResult.Valid.cricket: SavedCricketGame
    get() = saved as SavedCricketGame

/**
 * Like [playLegWonBy], except that [winner] first scores 20 points: T20, a single 20 while nobody else closed 20,
 * then the same closing darts as [playLegWonBy]. Nobody else scores.
 */
internal fun CricketMatch.playLegWonByScoring20(winner: Int): CricketMatch {
    val script = listOf(Dart.triple(20), Dart.single(20), Dart.MISS) +
        config.targets.filter { it != CricketTarget.TWENTY }.flatMap(::closingDarts)
    var match = this
    while (!match.currentLeg.state.isFinished) {
        val state = match.currentLeg.state
        val player = checkNotNull(state.currentPlayerIndex)
        val dart = if (player == winner) script[state.players[player].dartsThrown] else Dart.MISS
        match = match.throwDart(dart)
    }
    return match
}

/**
 * Ann (player 0) throws a T20 on each dart of [rounds] rounds while every other player misses: she closes the 20 with
 * the first dart then scores 60 per dart (1 020 points after six rounds, 4 digits).
 */
internal fun CricketMatch.annScoring(rounds: Int): CricketMatch {
    val others = currentLeg.state.players.size - 1
    var match = this
    repeat(rounds) {
        repeat(DARTS_PER_TURN) { match = match.throwDart(Dart.triple(20)) }
        repeat(others * DARTS_PER_TURN) { match = match.throwDart(Dart.MISS) }
    }
    return match
}

private const val DARTS_PER_TURN = 3
