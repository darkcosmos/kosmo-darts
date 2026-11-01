package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.testing.MatchJournals
import io.github.darkcosmos.kosmodarts.testing.TEST_STAMP
import io.github.darkcosmos.kosmodarts.testing.startedJournal
import io.github.darkcosmos.kosmodarts.testing.reduce
import io.github.darkcosmos.kosmodarts.testing.savedCricketGame
import io.github.darkcosmos.kosmodarts.testing.cricketGameScreen
import io.github.darkcosmos.kosmodarts.testing.cricketBetweenLegs
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

    private fun inGame(
        match: CricketMatch,
        isAwaiting: Boolean = false,
        journal: MatchJournal = MatchJournals.of(match),
    ) = AppState(
        screen = cricketGameScreen(CricketGameScreenState(match, isAwaitingConfirmation = isAwaiting), journal),
    )

    @Test
    fun `R12 R20 a dart and Undo save the Cricket game`() {
        val afterDart = duel.throwDart(Dart.single(20))
        val journal = MatchJournals.of(duel)
        val dartJournal = journal.enter(Dart.single(20), TEST_STAMP.at)

        assertEquals(
            listOf(PersistenceEffect.SaveGame(savedCricketGame(afterDart, false, journal = dartJournal))),
            effectsOf(inGame(duel), AppEvent.CricketDartTapped(Dart.single(20))),
        )
        assertEquals(
            listOf(PersistenceEffect.SaveGame(savedCricketGame(duel, false, journal = dartJournal.undo()))),
            effectsOf(inGame(afterDart, journal = dartJournal), AppEvent.UndoTapped),
        )
    }

    @Test
    fun `R12 Next player saves the end of the wait`() {
        val turn = duel.throwDart(Dart.MISS).throwDart(Dart.MISS).throwDart(Dart.MISS)

        assertEquals(
            listOf(PersistenceEffect.SaveGame(savedCricketGame(turn, isAwaitingNextPlayer = false))),
            effectsOf(inGame(turn, isAwaiting = true), AppEvent.NextPlayerTapped),
        )
    }

    @Test
    fun `R14 R15 Confirm leg saves the match between legs, Next leg saves the new leg`() {
        val confirmed = duel.confirmLegsWonBy(0)
        val between = AppState(screen = cricketBetweenLegs(confirmed))
        val betweenLegs = savedCricketGame(confirmed, isAwaitingNextPlayer = false, isBetweenLegs = true)

        assertEquals(
            listOf(PersistenceEffect.SaveGame(betweenLegs)),
            effectsOf(inGame(duel.playLegWonBy(0), isAwaiting = true), AppEvent.FinishGameTapped),
        )
        assertEquals(
            listOf(PersistenceEffect.SaveGame(savedCricketGame(confirmed, isAwaitingNextPlayer = false))),
            effectsOf(between, AppEvent.NextLegTapped),
        )
    }

    @Test
    fun `R6 R14 Confirm match and Abandon delete the saved game`() {
        val decided = inGame(duel.confirmLegsWonBy(0).playLegWonBy(0), isAwaiting = true)
        val dialog = reducer.reduce(inGame(duel), AppEvent.Back)
        val betweenDialog =
            AppState(screen = cricketBetweenLegs(duel.confirmLegsWonBy(0), isAbandonDialogShown = true))

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
                PersistenceEffect.SaveGame(
                    savedCricketGame(CricketMatch.start(rotated), false, journal = startedJournal()),
                ),
                PersistenceEffect.TouchProfiles(listOf("Bob", "Ann"), startedJournal().startedAt),
            ),
            persistenceEffects(end, rematch),
        )
        assertEquals(rotated, rematch.lastSetup?.cricketSetup)
    }
}
