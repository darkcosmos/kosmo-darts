package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.testing.reduce
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures
import kotlin.random.Random

/**
 * Records the events a player would send to play a match from the home screen, while following the state with the
 * real reducer, so that screen tests and captures can replay them on the ViewModel (docs/rules/match.md).
 */
internal class MatchScript {

    private val reducer = AppReducer(Random(0))
    private val helper = MatchEvents(reducer)

    var state: AppState = AppState()
        private set

    val events: MutableList<AppEvent> = mutableListOf()

    private fun send(vararg sent: AppEvent): MatchScript {
        sent.forEach { event ->
            events += event
            state = reducer.reduce(state, event)
        }
        return this
    }

    /** "New game", the names, 501 or 301 single out, the format, then Start. */
    fun start(
        vararg names: String,
        legsPerSet: Int = 1,
        sets: Int = 1,
        startScore: Int = 301,
        singleOut: Boolean = true,
    ): MatchScript {
        send(AppEvent.NewGame(GameMode.X01))
        repeat(names.size - 2) { send(AppEvent.PlayerAdded) }
        if (names.size == 1) send(AppEvent.PlayerRemoved(1))
        names.forEachIndexed { index, name -> send(AppEvent.NameChanged(index, name)) }
        send(AppEvent.StartScoreSelected(startScore))
        if (singleOut) send(AppEvent.CheckoutRuleSelected(CheckoutRule.SINGLE_OUT))
        repeat(legsPerSet - 1) { send(AppEvent.LegsPerSetChanged(1)) }
        repeat(sets - 1) { send(AppEvent.SetsChanged(1)) }
        return send(AppEvent.StartRequested)
    }

    /** Plays the current leg until [winner] checks out: the checkout dialog is then open. */
    fun playLegWonBy(winner: Int): MatchScript {
        val match = (state.screen as Screen.Game).game.match
        MatchFixtures.dartsToWin(match, winner).forEach { dart ->
            send(*helper.eventsOf(dart).toTypedArray())
            if ((state.screen as Screen.Game).game.canGoToNextPlayer) send(AppEvent.NextPlayerTapped)
        }
        return this
    }

    /** "Finish …" in the checkout dialog. */
    fun finish(): MatchScript = send(AppEvent.FinishGameTapped)

    fun nextLeg(): MatchScript = send(AppEvent.NextLegTapped)

    /** Plays, finishes and, between legs, goes on to the next leg, for each winner in order. */
    fun legsWonBy(vararg winners: Int): MatchScript {
        winners.forEach { winner ->
            playLegWonBy(winner).finish()
            if (state.screen is Screen.BetweenLegs) nextLeg()
        }
        return this
    }

    /** Any other events, such as darts or "Next player". */
    fun then(vararg sent: AppEvent): MatchScript = send(*sent)
}
