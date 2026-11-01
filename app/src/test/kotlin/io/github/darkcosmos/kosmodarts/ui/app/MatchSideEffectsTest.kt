package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.testing.savedGame
import io.github.darkcosmos.kosmodarts.testing.gameScreen
import io.github.darkcosmos.kosmodarts.testing.betweenLegs
import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.x01.X01Cue
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures.match
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cues and storage of the match flow (docs/rules/announcements.md, AN27, AN28; docs/rules/persistence.md, R14,
 * R15).
 */
class MatchSideEffectsTest {

    private val events = MatchEvents()

    private fun inGame(match: X01Match) = AppState(screen = gameScreen(GameScreenState(match)))

    private fun gameOf(state: AppState): GameScreenState = (state.screen as Screen.Game).game

    /** The state just before the winning dart of the current leg, and the events of that dart. */
    private fun beforeWinningDart(start: AppState, winner: Int): Pair<AppState, List<AppEvent>> {
        val darts = MatchFixtures.dartsToWin(gameOf(start).match, winner)
        val before = darts.dropLast(1).fold(start) { state, dart ->
            val thrown = events.after(state, *events.eventsOf(dart).toTypedArray())
            if (gameOf(thrown).canGoToNextPlayer) events.after(thrown, AppEvent.NextPlayerTapped) else thrown
        }
        return before to events.eventsOf(darts.last())
    }

    /** The cues of the last of [eventList], applied from [start]. */
    private fun cuesOfLast(start: AppState, eventList: List<AppEvent>): List<X01Cue> {
        val before = events.after(start, *eventList.dropLast(1).toTypedArray())
        val event = eventList.last()
        return gameCues(before, event, events.after(before, event))
    }

    /** AN27: the win cue of the winning dart carries LEG, SET or MATCH. */
    @Test
    fun `AN27 the winning dart tells what it wins`() {
        val start = match("Ann", "Bob", legsPerSet = 2, sets = 2)
        mapOf(
            start to LegOutcome.LEG,
            start.confirmLegsWonBy(0) to LegOutcome.SET,
            start.confirmLegsWonBy(0, 0, 0) to LegOutcome.MATCH,
        ).forEach { (match, outcome) ->
            val (before, dartEvents) = beforeWinningDart(inGame(match), winner = 0)

            val win = cuesOfLast(before, dartEvents).filterIsInstance<X01Cue.Win>().single()

            assertEquals(outcome, win.outcome)
        }
    }

    /** AN28: "Next leg" announces the starter of the new leg with the "throws first" line. */
    @Test
    fun `AN28 Next leg announces the new starter`() {
        val next = match("Ann", "Bob", "Cid", legsPerSet = 2).confirmLegsWonBy(0)
        val between = AppState(screen = betweenLegs(next))

        assertEquals(
            listOf(X01Cue.TurnStart(1, "Bob", null, isGameStart = true)),
            gameCues(between, AppEvent.NextLegTapped, events.after(between, AppEvent.NextLegTapped)),
        )
    }

    /** AN7, AN28: "Finish leg", back, the abandon dialog and a resume on the between-legs screen say nothing. */
    @Test
    fun `AN7 nothing else around the between-legs screen produces cues`() {
        val won = events.playLegWonBy(inGame(match("Ann", "Bob", legsPerSet = 2)), winner = 0)
        val between = events.after(won, AppEvent.FinishGameTapped)
        val asking = events.after(between, AppEvent.Back)
        val saved = savedGame((between.screen as Screen.BetweenLegs).match, false, isBetweenLegs = true)
        val home = AppState(screen = Screen.Home(resumableGame = saved))
        listOf(
            Triple(won, AppEvent.FinishGameTapped, between),
            Triple(between, AppEvent.Back, asking),
            Triple(asking, AppEvent.AbandonDismissed, between),
            Triple(home, AppEvent.ResumeTapped, events.after(home, AppEvent.ResumeTapped)),
        ).forEach { (before, event, after) ->
            assertEquals(event.toString(), emptyList<X01Cue>(), gameCues(before, event, after))
        }
    }

    /** R15: "Finish leg", "Finish set" and "Next leg" write the match; the between-legs screen keeps it saved. */
    @Test
    fun `R15 Finish leg, Finish set and Next leg write the match`() {
        val start = match("Ann", "Bob", legsPerSet = 2, sets = 2)
        listOf(start, start.confirmLegsWonBy(0)).forEach { match ->
            val won = events.playLegWonBy(inGame(match), winner = 0)
            val between = events.after(won, AppEvent.FinishGameTapped)
            val confirmed = (between.screen as Screen.BetweenLegs).match
            val journal = (between.screen as Screen.BetweenLegs).journal

            assertEquals(
                listOf(PersistenceEffect.SaveGame(savedGame(confirmed, false, isBetweenLegs = true, journal))),
                persistenceEffects(won, between),
            )
            val next = events.after(between, AppEvent.NextLegTapped)
            assertEquals(
                listOf(PersistenceEffect.SaveGame(savedGame(confirmed, false, journal = journal))),
                persistenceEffects(between, next),
            )
        }
    }

    /** R14, R6: the match is deleted only on "Finish game" of the match, and on abandon from either screen. */
    @Test
    fun `R14 the match is deleted only at its end or on abandon`() {
        val start = match("Ann", "Bob", legsPerSet = 2)
        val matchWon = events.playLegWonBy(inGame(start.confirmLegsWonBy(0)), winner = 0)
        assertEquals(
            listOf(PersistenceEffect.DeleteGame),
            persistenceEffects(matchWon, events.after(matchWon, AppEvent.FinishGameTapped)),
        )
        val between = AppState(screen = betweenLegs(start.confirmLegsWonBy(1)))
        val asking = events.after(between, AppEvent.Back)
        assertEquals(emptyList<PersistenceEffect>(), persistenceEffects(between, asking))
        assertEquals(
            listOf(PersistenceEffect.DeleteGame),
            persistenceEffects(asking, events.after(asking, AppEvent.AbandonConfirmed)),
        )
        val inSecondLeg = events.after(between, AppEvent.NextLegTapped, AppEvent.Back)
        assertEquals(
            listOf(PersistenceEffect.DeleteGame),
            persistenceEffects(inSecondLeg, events.after(inSecondLeg, AppEvent.AbandonConfirmed)),
        )
    }

    /** R15 (T3): resuming a match saved between legs writes nothing. */
    @Test
    fun `R15 resuming on the between-legs screen writes nothing`() {
        val saved = savedGame(match("Ann", "Bob", legsPerSet = 2).confirmLegsWonBy(0), false, isBetweenLegs = true)
        val home = AppState(screen = Screen.Home(resumableGame = saved))

        val resumed = events.after(home, AppEvent.ResumeTapped)

        assertEquals(emptyList<PersistenceEffect>(), persistenceEffects(home, resumed))
    }
}
