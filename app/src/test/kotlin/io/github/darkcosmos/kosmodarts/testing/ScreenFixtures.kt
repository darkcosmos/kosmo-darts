package io.github.darkcosmos.kosmodarts.testing

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.persistence.SavedCricketGame
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.AppReducer
import io.github.darkcosmos.kosmodarts.ui.app.AppState
import io.github.darkcosmos.kosmodarts.ui.app.EventStamp
import io.github.darkcosmos.kosmodarts.ui.app.NoVoiceNotice
import io.github.darkcosmos.kosmodarts.ui.app.Screen
import io.github.darkcosmos.kosmodarts.ui.cricket.CricketGameScreenState
import io.github.darkcosmos.kosmodarts.ui.game.CheckoutSummary
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState

// Builders of saved games and screens whose journal follows from their match (see [MatchJournals]), so that the many
// tests of the screens, the reducers and the codecs state only what they test.

/** The stamp of [reduce] in tests that do not look at times and ids. */
val TEST_STAMP = EventStamp(at = 1_000L, matchId = "11111111-1111-4111-8111-111111111111")

/** Reduces [event] with [TEST_STAMP]. */
fun AppReducer.reduce(state: AppState, event: AppEvent): AppState = reduce(state, event, TEST_STAMP)

fun savedGame(
    match: X01Match,
    isAwaitingNextPlayer: Boolean,
    isBetweenLegs: Boolean = false,
    journal: MatchJournal = MatchJournals.of(match),
): SavedGame = SavedGame(match, isAwaitingNextPlayer, isBetweenLegs, journal)

/** A single game (M5). */
fun savedGame(game: X01Game, isAwaitingNextPlayer: Boolean, journal: MatchJournal? = null): SavedGame {
    val match = X01Match(X01MatchConfig(game.config), emptyList(), game)
    return when (journal) {
        null -> savedGame(match, isAwaitingNextPlayer)
        else -> savedGame(match, isAwaitingNextPlayer, journal = journal)
    }
}

fun savedCricketGame(
    match: CricketMatch,
    isAwaitingNextPlayer: Boolean,
    isBetweenLegs: Boolean = false,
    journal: MatchJournal = MatchJournals.of(match),
): SavedCricketGame = SavedCricketGame(match, isAwaitingNextPlayer, isBetweenLegs, journal)

fun gameScreen(
    game: GameScreenState,
    journal: MatchJournal = MatchJournals.of(game.match),
    isNewGame: Boolean = false,
    noVoiceNotice: NoVoiceNotice? = null,
): Screen.Game = Screen.Game(game, journal, isNewGame, noVoiceNotice)

fun cricketGameScreen(
    game: CricketGameScreenState,
    journal: MatchJournal = MatchJournals.of(game.match),
    isNewGame: Boolean = false,
    noVoiceNotice: NoVoiceNotice? = null,
): Screen.CricketGame = Screen.CricketGame(game, journal, isNewGame, noVoiceNotice)

fun betweenLegs(
    match: X01Match,
    journal: MatchJournal = MatchJournals.of(match),
    isAbandonDialogShown: Boolean = false,
    checkout: CheckoutSummary? = null,
): Screen.BetweenLegs = Screen.BetweenLegs(match, journal, isAbandonDialogShown, checkout)

fun cricketBetweenLegs(
    match: CricketMatch,
    journal: MatchJournal = MatchJournals.of(match),
    isAbandonDialogShown: Boolean = false,
): Screen.CricketBetweenLegs = Screen.CricketBetweenLegs(match, journal, isAbandonDialogShown)

/** The journal of a match just started by "Start" or "Rematch" with [TEST_STAMP]: its id and start, no dart. */
fun startedJournal(): MatchJournal = MatchJournal(TEST_STAMP.matchId, TEST_STAMP.at)
