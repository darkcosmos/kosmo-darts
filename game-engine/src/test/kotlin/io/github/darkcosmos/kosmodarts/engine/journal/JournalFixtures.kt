package io.github.darkcosmos.kosmodarts.engine.journal

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.dart
import org.junit.jupiter.api.Assertions.assertEquals

/** Token that stands for an undo in the journals of the tests; any other token is a dart code (see `dart`). */
internal const val UNDO = "U"

internal fun x01Setup(
    vararg players: String,
    startScore: Int = 301,
    checkoutRule: CheckoutRule = CheckoutRule.DOUBLE_OUT,
    starter: Int = 0,
): LegSetup = LegSetup.X01(X01Config(players.toList(), startScore, checkoutRule), starter)

internal fun cricketSetup(vararg players: String, starter: Int = 0): LegSetup =
    LegSetup.Cricket(CricketConfig(players.toList()), starter)

/** Builds a leg journal from dart codes and [UNDO]; the entry time of the dart number n (from 0) is 1000 + n. */
internal fun journalOf(vararg tokens: String): LegJournal =
    tokens.foldIndexed(LegJournal()) { index, journal, token ->
        journal.with(
            if (token == UNDO) JournalEvent.Undone else JournalEvent.DartEntered(dart(token), 1000L + index),
        )
    }

internal fun replay(setup: LegSetup, vararg tokens: String): ReplayedLeg = LegReplay.replay(journalOf(*tokens), setup)

/** DJ4: the darts in play equal the darts of the engine leg driven directly with the same throws and undos. */
internal fun assertDartsInPlayMatchEngine(setup: LegSetup, vararg tokens: String) {
    val game = tokens.fold(setup.newGame()) { g, token -> if (token == UNDO) g.undo() else g.throwDart(dart(token)) }
    val replayed = replay(setup, *tokens)
    assertEquals(game.darts, replayed.dartsInPlay, "DJ4")
    assertEquals(game.darts, replayed.darts.filterNot { it.isVoided }.map { it.dart }, "DJ4 records")
    assertEquals(game.isFinished, replayed.isFinished)
}

/** (player, visit, rank) of every record. */
internal fun ReplayedLeg.positions(): List<Triple<Int, Int, Int>> =
    darts.map { Triple(it.playerIndex, it.visit, it.rank) }

internal fun ReplayedLeg.busts(): List<Boolean> = darts.map { it.isBust }

internal fun ReplayedLeg.voids(): List<Boolean> = darts.map { it.isVoided }

/** Three visits of a one-player 301 double-out leg, the last dart (D20) winning at visit 3, rank 1. */
internal val WIN_301_ONE_DART: Array<String> = arrayOf("T20", "T20", "T20", "T20", "T7", "MISS", "D20")

/** Same leg won at visit 3, rank 2 (T19 then D2). */
internal val WIN_301_TWO_DARTS: Array<String> = arrayOf("T20", "T20", "T20", "T20", "MISS", "MISS", "T19", "D2")

/** One-player Cricket leg closing every target; the last dart is the second BULL (rank 2 of visit 3). */
internal val WIN_CRICKET: Array<String> =
    arrayOf("T20", "T19", "T18", "T17", "T16", "T15", "BULL", "BULL")
