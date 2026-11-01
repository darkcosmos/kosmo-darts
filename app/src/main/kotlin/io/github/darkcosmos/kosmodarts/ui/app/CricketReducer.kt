package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatchConfig
import io.github.darkcosmos.kosmodarts.engine.journal.GameProgress
import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.persistence.LastSetups
import io.github.darkcosmos.kosmodarts.ui.cricket.CricketGameScreenState

// The Cricket screens of [AppReducer] (docs/rules/cricket.md, CR22 to CR28), kept apart so that the X01 branches stay
// as they are (plan decision D1). Pure.

/** The Cricket game screen after [event]: play, "Finish …", abandon and the no-voice message (CR22 to CR25). */
internal fun onCricketGame(state: AppState, screen: Screen.CricketGame, event: AppEvent, at: Long): AppState {
    val game = screen.game
    return when {
        // AN16: the message is modal; "OK", back and "Install a voice" close it.
        screen.noVoiceNotice != null -> when (event) {
            AppEvent.NoVoiceNoticeDismissed, AppEvent.InstallVoiceTapped, AppEvent.Back ->
                state.copy(screen = screen.copy(noVoiceNotice = null))
            else -> state
        }
        event is AppEvent.VoiceUnavailable ->
            state.copy(screen = screen.copy(noVoiceNotice = NoVoiceNotice(event.canInstall)))
        // Rule R6: leaving the game screen this way deletes the saved game.
        event == AppEvent.AbandonConfirmed -> state.copy(screen = Screen.Home())
        event == AppEvent.FinishGameTapped && game.isWinPending -> state.copy(screen = finishCricket(screen, at))
        // M15, owner O1: system back opens "Abandon the game?" in every zone mode, a pending win included; dismissing
        // it leaves the win pending.
        else -> state.copy(screen = playCricket(screen, event, at))
    }
}

/** M16: "Next leg", or back that asks "Abandon the game?" (owner Q6 i, Q7). */
internal fun onCricketBetweenLegs(state: AppState, screen: Screen.CricketBetweenLegs, event: AppEvent): AppState =
    when {
        screen.isAbandonDialogShown -> when (event) {
            // Rule R6: abandoning deletes the whole match (R14, R20).
            AppEvent.AbandonConfirmed -> state.copy(screen = Screen.Home())
            AppEvent.AbandonDismissed, AppEvent.Back -> state.copy(screen = screen.copy(isAbandonDialogShown = false))
            else -> state
        }
        event == AppEvent.NextLegTapped ->
            state.copy(screen = Screen.CricketGame(CricketGameScreenState(screen.match), screen.journal))
        event == AppEvent.Back -> state.copy(screen = screen.copy(isAbandonDialogShown = true))
        else -> state
    }

/**
 * M17, CR28: back returns home, "New game" opens the setup of its mode, "Rematch" starts the same match with the first
 * player moved to the end.
 */
internal fun onCricketEnd(
    state: AppState,
    screen: Screen.CricketEnd,
    event: AppEvent,
    stamp: EventStamp,
): AppState = when (event) {
    AppEvent.Back -> state.copy(screen = Screen.Home())
    is AppEvent.NewGame -> state.toSetup(event.mode)
    // Rule R22: a rematch is a game start, so its roster and settings become the last setup.
    AppEvent.Rematch -> newCricketMatchState(cricketRematchSetup(screen.match), state.lastSetup, stamp)
    else -> state
}

/**
 * A new Cricket match from Start or Rematch: its roster and Cricket settings become the last setup, and the settings
 * of the other modes in [previous] are kept (R22).
 */
internal fun newCricketMatchState(
    setup: CricketMatchConfig,
    previous: LastSetups?,
    stamp: EventStamp,
): AppState = AppState(
    screen = Screen.CricketGame(
        CricketGameScreenState(CricketMatch.start(setup)),
        MatchJournal(stamp.matchId, stamp.at),
        isNewGame = true,
    ),
    lastSetup = LastSetups.afterCricket(previous, setup),
)

/**
 * The setup of a Cricket rematch (CR28, M17): same players, variant, 25-Bull and format; the first player moves to the
 * end, so the next one starts the first leg (A, B, C -> B, C, A). Each player keeps his color slot (PC7).
 */
internal fun cricketRematchSetup(match: CricketMatch): CricketMatchConfig {
    val config = match.config
    val players = config.players
    val rotated = CricketConfig(players.drop(1) + players.first(), config.variant, config.isBullInPlay)
    val slots = match.colorSlots
    return CricketMatchConfig(rotated, match.format, slots.drop(1) + slots.first())
}

/** M15, M16, M17: "Confirm match" ends the match; "Confirm leg" and "Confirm set" confirm the leg. */
private fun finishCricket(screen: Screen.CricketGame, at: Long): Screen {
    val game = screen.game
    return when (game.winOutcome) {
        LegOutcome.LEG, LegOutcome.SET -> {
            val confirmed = game.match.confirmLeg()
            Screen.CricketBetweenLegs(confirmed, GameProgress.follow(screen.journal, game.match, confirmed, at))
        }
        LegOutcome.MATCH, null -> Screen.CricketEnd(game.match)
    }
}

/** DJ1, DJ2: the screen after a play event, with the journal that goes with its match. */
private fun playCricket(screen: Screen.CricketGame, event: AppEvent, at: Long): Screen.CricketGame {
    val next = playCricket(screen.game, event)
    return screen.copy(game = next, journal = GameProgress.follow(screen.journal, screen.game.match, next.match, at))
}

private fun playCricket(game: CricketGameScreenState, event: AppEvent): CricketGameScreenState = when (event) {
    AppEvent.Back -> game.withAbandonDialog(shown = true)
    AppEvent.AbandonDismissed -> game.withAbandonDialog(shown = false)
    is AppEvent.CricketDartTapped -> game.throwDart(event.dart)
    AppEvent.MissTapped -> game.throwMiss()
    AppEvent.UndoTapped -> game.undo()
    AppEvent.NextPlayerTapped -> game.nextPlayer()
    else -> game
}
