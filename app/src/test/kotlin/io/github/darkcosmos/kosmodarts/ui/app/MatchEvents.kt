package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.testing.reduce
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures
import kotlin.random.Random

/** Drives the reducer like the keypad does, for the match tests (docs/rules/match.md). */
internal class MatchEvents(private val reducer: AppReducer = AppReducer(Random(0))) {

    fun after(state: AppState, vararg events: AppEvent): AppState = events.fold(state, reducer::reduce)

    /** The keypad events of [dart]. */
    fun eventsOf(dart: Dart): List<AppEvent> = when {
        dart == Dart.MISS -> listOf(AppEvent.MissTapped)
        dart == Dart.BULL -> listOf(AppEvent.BullTapped)
        dart == Dart.OUTER_BULL -> listOf(AppEvent.OuterBullTapped)
        dart.multiplier == Multiplier.SINGLE -> listOf(AppEvent.NumberTapped(dart.segment))
        else -> listOf(AppEvent.MultiplierTapped(dart.multiplier), AppEvent.NumberTapped(dart.segment))
    }

    /**
     * From the game screen, plays the current leg until [winner] checks out, with "Next player" after every other
     * completed turn: the state is then in the checkout dialog.
     */
    fun playLegWonBy(state: AppState, winner: Int): AppState {
        val match = (state.screen as Screen.Game).game.match
        return MatchFixtures.dartsToWin(match, winner).fold(state) { current, dart ->
            val thrown = after(current, *eventsOf(dart).toTypedArray())
            val game = (thrown.screen as Screen.Game).game
            if (game.canGoToNextPlayer) after(thrown, AppEvent.NextPlayerTapped) else thrown
        }
    }

    /** Plays the leg, then "Finish …" and, unless the match is over, "Next leg". */
    fun legWonBy(state: AppState, winner: Int): AppState {
        val finished = after(playLegWonBy(state, winner), AppEvent.FinishGameTapped)
        return if (finished.screen is Screen.BetweenLegs) after(finished, AppEvent.NextLegTapped) else finished
    }
}
