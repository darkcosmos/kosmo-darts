package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.SavedCricketGame
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.persistence.playLegWonBy
import io.github.darkcosmos.kosmodarts.ui.cricket.CricketGameScreenState
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** What each Cricket event writes or deletes (docs/rules/persistence.md, R6, R12, R14, R15, R20, R22). */
class CricketPersistenceEffectsTest {

    private val reducer = AppReducer(Random(0))

    private val duel = CricketSaveFixtures.match("Ann", "Bob", legsPerSet = 2)

    private fun effectsOf(before: AppState, event: AppEvent): List<PersistenceEffect> =
        persistenceEffects(before, reducer.reduce(before, event))

    private fun inGame(match: CricketMatch, isAwaiting: Boolean = false) =
        AppState(screen = Screen.CricketGame(CricketGameScreenState(match, isAwaitingConfirmation = isAwaiting)))

    @Test
    fun `R12 R20 a dart and Undo save the Cricket game`() {
        val afterDart = duel.throwDart(Dart.single(20))

        assertEquals(
            listOf(PersistenceEffect.SaveGame(SavedCricketGame(afterDart, isAwaitingNextPlayer = false))),
            effectsOf(inGame(duel), AppEvent.CricketDartTapped(Dart.single(20))),
        )
        assertEquals(
            listOf(PersistenceEffect.SaveGame(SavedCricketGame(duel, isAwaitingNextPlayer = false))),
            effectsOf(inGame(afterDart), AppEvent.UndoTapped),
        )
    }

    @Test
    fun `R12 Next player saves the end of the wait`() {
        val turn = duel.throwDart(Dart.MISS).throwDart(Dart.MISS).throwDart(Dart.MISS)

        assertEquals(
            listOf(PersistenceEffect.SaveGame(SavedCricketGame(turn, isAwaitingNextPlayer = false))),
            effectsOf(inGame(turn, isAwaiting = true), AppEvent.NextPlayerTapped),
        )
    }

    @Test
    fun `R14 R15 Confirm leg saves the match between legs, Next leg saves the new leg`() {
        val confirmed = duel.confirmLegsWonBy(0)
        val between = AppState(screen = Screen.CricketBetweenLegs(confirmed))
        val betweenLegs = SavedCricketGame(confirmed, isAwaitingNextPlayer = false, isBetweenLegs = true)

        assertEquals(
            listOf(PersistenceEffect.SaveGame(betweenLegs)),
            effectsOf(inGame(duel.playLegWonBy(0), isAwaiting = true), AppEvent.FinishGameTapped),
        )
        assertEquals(
            listOf(PersistenceEffect.SaveGame(SavedCricketGame(confirmed, isAwaitingNextPlayer = false))),
            effectsOf(between, AppEvent.NextLegTapped),
        )
    }

    @Test
    fun `R6 R14 Confirm match and Abandon delete the saved game`() {
        val decided = inGame(duel.confirmLegsWonBy(0).playLegWonBy(0), isAwaiting = true)
        val dialog = reducer.reduce(inGame(duel), AppEvent.Back)
        val betweenDialog =
            AppState(screen = Screen.CricketBetweenLegs(duel.confirmLegsWonBy(0), isAbandonDialogShown = true))

        assertEquals(listOf(PersistenceEffect.DeleteGame), effectsOf(decided, AppEvent.FinishGameTapped))
        assertEquals(listOf(PersistenceEffect.DeleteGame), effectsOf(dialog, AppEvent.AbandonConfirmed))
        assertEquals(listOf(PersistenceEffect.DeleteGame), effectsOf(betweenDialog, AppEvent.AbandonConfirmed))
    }

    @Test
    fun `R12 the abandon dialog and a refused dart write nothing`() {
        assertEquals(emptyList<PersistenceEffect>(), effectsOf(inGame(duel), AppEvent.Back))
        val noBull = CricketSaveFixtures.match("Ann", "Bob", isBullInPlay = false)
        assertEquals(
            emptyList<PersistenceEffect>(),
            effectsOf(inGame(noBull), AppEvent.CricketDartTapped(Dart.OUTER_BULL)),
        )
    }

    @Test
    fun `R22 Rematch saves the rotated setup and the new game`() {
        val end = AppState(screen = Screen.CricketEnd(duel.confirmLegsWonBy(0).playLegWonBy(0)))
        val rematch = reducer.reduce(end, AppEvent.Rematch)
        val rotated = cricketRematchSetup(duel)

        assertEquals(
            listOf(
                PersistenceEffect.SaveLastSetup(checkNotNull(rematch.lastSetup)),
                PersistenceEffect.SaveGame(SavedCricketGame(CricketMatch.start(rotated), isAwaitingNextPlayer = false)),
            ),
            persistenceEffects(end, rematch),
        )
        assertEquals(rotated, rematch.lastSetup?.cricketSetup)
    }
}
