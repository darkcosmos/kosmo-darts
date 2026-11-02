package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.testing.TEST_STAMP
import io.github.darkcosmos.kosmodarts.testing.reduce
import io.github.darkcosmos.kosmodarts.testing.cricketGameScreen
import io.github.darkcosmos.kosmodarts.testing.cricketBetweenLegs
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketCue
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.persistence.playLegWonBy
import io.github.darkcosmos.kosmodarts.persistence.throwing
import io.github.darkcosmos.kosmodarts.ui.cricket.CricketGameScreenState
import io.github.darkcosmos.kosmodarts.ui.cricket.toGameScreenState
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** The Cricket cues of the game ViewModel (docs/rules/announcements.md, AN7, AN27 to AN31). */
class CricketGameCuesTest {

    private val reducer = AppReducer(Random(0))

    private val duel = CricketSaveFixtures.match("Ann", "Bob", legsPerSet = 2)

    private fun inGame(match: CricketMatch = duel, isAwaiting: Boolean = false) =
        AppState(screen = cricketGameScreen(CricketGameScreenState(match, isAwaitingConfirmation = isAwaiting)))

    private fun dartTapped(segment: Int) = AppEvent.CricketDartTapped(Dart.single(segment))

    /** Applies [events] in order and returns the Cricket cues of the last one. */
    private fun cuesOf(start: AppState, vararg events: AppEvent): List<CricketCue> {
        var state = start
        var cues = emptyList<CricketCue>()
        events.forEach { event ->
            val next = reducer.reduce(state, event)
            cues = cricketGameCues(state, event, next)
            state = next
        }
        return cues
    }

    @Test
    fun `AN29 a dart gives its hit or miss cue`() {
        assertEquals(listOf(CricketCue.DartHit(Dart.single(20))), cuesOf(inGame(), dartTapped(20)))
        assertEquals(listOf(CricketCue.DartMiss), cuesOf(inGame(), AppEvent.MissTapped))
        // CR3: 14 is not a target, so it gives no mark.
        assertEquals(listOf(CricketCue.DartMiss), cuesOf(inGame(), dartTapped(14)))
    }

    @Test
    fun `AN30 CR15 a dart on a closed sector gives the closed-sector cue`() {
        val closed = duel.throwing(Dart.triple(20), Dart.MISS, Dart.MISS, Dart.triple(20), Dart.MISS, Dart.MISS)

        assertEquals(
            listOf(CricketCue.ClosedSectorHit(Dart.single(20))),
            cuesOf(inGame(closed), dartTapped(20)),
        )
    }

    @Test
    fun `AN27 the winning dart carries what the leg decides`() {
        val beforeLegWin = duel.playLegWonBy(0).undo()
        val legCues = cuesOf(inGame(beforeLegWin), AppEvent.CricketDartTapped(Dart.OUTER_BULL))
        val decided = duel.confirmLegsWonBy(0).playLegWonBy(0).undo()
        val matchCues = cuesOf(inGame(decided), AppEvent.CricketDartTapped(Dart.OUTER_BULL))

        assertEquals(listOf(CricketCue.DartHit(Dart.OUTER_BULL), CricketCue.Win(0, "Ann", LegOutcome.LEG)), legCues)
        assertEquals(CricketCue.Win(0, "Ann", LegOutcome.MATCH), matchCues.last())
    }

    @Test
    fun `AN9 Undo gives the undo cue, a refused dart gives none`() {
        assertEquals(listOf(CricketCue.DartUndone), cuesOf(inGame(), dartTapped(20), AppEvent.UndoTapped))
        // CR1, CR24: with 25-Bull off, a tap on the 25 is ignored and makes no sound.
        val noBull = inGame(CricketSaveFixtures.match("Ann", "Bob", isBullInPlay = false))
        assertEquals(emptyList<CricketCue>(), cuesOf(noBull, AppEvent.CricketDartTapped(Dart.OUTER_BULL)))
    }

    @Test
    fun `AN31 Next player announces the next turn`() {
        val waiting = inGame(duel.throwing(Dart.MISS, Dart.MISS, Dart.MISS), isAwaiting = true)

        assertEquals(
            listOf(CricketCue.TurnStart(1, "Bob", isGameStart = false)),
            cuesOf(waiting, AppEvent.NextPlayerTapped),
        )
    }

    @Test
    fun `AN31 a new match and a rematch announce who throws first`() {
        val start = newCricketMatchState(duel.matchConfig, previous = null, stamp = TEST_STAMP)
        val cues = cricketGameCues(AppState(screen = Screen.Setup(SetupState())), AppEvent.StartRequested, start)
        val end = AppState(screen = Screen.CricketEnd(duel.confirmLegsWonBy(0).playLegWonBy(0)))

        assertEquals(listOf(CricketCue.TurnStart(0, "Ann", isGameStart = true)), cues)
        assertEquals(listOf(CricketCue.TurnStart(0, "Bob", isGameStart = true)), cuesOf(end, AppEvent.Rematch))
    }

    @Test
    fun `AN28 Next leg announces who throws first in the new leg`() {
        val between = AppState(screen = cricketBetweenLegs(duel.confirmLegsWonBy(0)))

        assertEquals(
            listOf(CricketCue.TurnStart(1, "Bob", isGameStart = true)),
            cuesOf(between, AppEvent.NextLegTapped),
        )
    }

    @Test
    fun `AN7 no cue on a finish, an abandon or a resume`() {
        val won = AppState(
            screen = cricketGameScreen(CricketGameScreenState(duel.playLegWonBy(0), isAwaitingConfirmation = true)),
        )
        val resumed = AppState(screen = CricketSaveFixtures.WAITING.toScreen())

        assertEquals(emptyList<CricketCue>(), cuesOf(won, AppEvent.FinishGameTapped))
        assertEquals(emptyList<CricketCue>(), cuesOf(won, AppEvent.Back, AppEvent.AbandonConfirmed))
        assertEquals(emptyList<CricketCue>(), cricketGameCues(AppState(), AppEvent.ResumeTapped, resumed))
        assertEquals(CricketSaveFixtures.WAITING.toGameScreenState(), (resumed.screen as Screen.CricketGame).game)
    }
}
