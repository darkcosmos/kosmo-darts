package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.HistoryList
import io.github.darkcosmos.kosmodarts.history.MatchDetail
import io.github.darkcosmos.kosmodarts.history.Profile
import io.github.darkcosmos.kosmodarts.persistence.LastSetups
import io.github.darkcosmos.kosmodarts.persistence.StoredGame
import io.github.darkcosmos.kosmodarts.ui.cricket.CricketGameScreenState
import io.github.darkcosmos.kosmodarts.ui.game.CheckoutSummary
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState
import io.github.darkcosmos.kosmodarts.ui.history.HistoryState
import io.github.darkcosmos.kosmodarts.ui.history.MatchDetailState
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState

/** The screen on display, with its content. There is no navigation library: the screen is plain state. */
sealed interface Screen {
    /**
     * @property resumableGame the saved game shown in the summary, with "Resume game" (rule R2), or `null`.
     * @property newGameDialogMode the mode of the "New game" that asks to delete the saved game (rules R3, R20), or
     *   `null` while the confirmation is closed; confirming it opens the setup of that mode.
     */
    data class Home(val resumableGame: StoredGame? = null, val newGameDialogMode: GameMode? = null) : Screen {
        /** Whether "New game" asks to delete the saved game (rule R3). */
        val isNewGameDialogShown: Boolean
            get() = newGameDialogMode != null
    }

    /** @property isRestoreFailedShown whether the "saved game could not be resumed" message is open (rule R9). */
    data class Setup(val setup: SetupState, val isRestoreFailedShown: Boolean = false) : Screen

    /**
     * @property journal the journal of the match, in step with [game] (DJ1, DJ4); saved with it.
     * @property isNewGame whether "Start" or "Rematch" opened this screen, rather than a resume (AN4, AN16).
     * @property noVoiceNotice the open "no offline voice" message (AN16), or `null`. Neither is saved.
     */
    data class Game(
        val game: GameScreenState,
        val journal: MatchJournal,
        val isNewGame: Boolean = false,
        val noVoiceNotice: NoVoiceNotice? = null,
    ) : Screen

    /** The "Settings" screen (AN13); Back returns to [home], with its saved game summary. */
    data class Settings(val home: Home) : Screen

    /**
     * The History list (HS1 to HS4, HS7); Back returns to [home], with its saved game summary. The filters and the
     * scroll position live in [history] so that Back from the detail can restore them (HS6).
     */
    data class History(val home: Home, val history: HistoryState = HistoryState()) : Screen

    /**
     * The read-only detail of one archived match (HS5, HS6). It keeps the whole [list] screen it came from, so that
     * Back returns to History with its filters, matches and scroll position, without a new read.
     */
    data class MatchDetail(val list: History, val detail: MatchDetailState) : Screen

    /**
     * Between two legs of a match, after "Finish leg" or "Finish set" (docs/rules/match.md, M16). [match] already has
     * the leg confirmed and its next leg started, with no dart.
     *
     * @property journal the journal of the match: the legs confirmed so far and the empty journal of the next leg.
     * @property isAbandonDialogShown whether the "Abandon the game?" confirmation is open (owner Q6 i).
     * @property checkout the winning visit of the leg just confirmed, shown in the winner's zone; `null` after a resume
     *   on this screen (not saved).
     */
    data class BetweenLegs(
        val match: X01Match,
        val journal: MatchJournal,
        val isAbandonDialogShown: Boolean = false,
        val checkout: CheckoutSummary? = null,
    ) : Screen

    /** End of a finished match (M17); a single game is a match of one leg. */
    data class End(val match: X01Match) : Screen {
        /** The end of a single game (M5): [game] is its only, finished, leg. */
        constructor(game: X01Game) : this(X01Match(X01MatchConfig(game.config), emptyList(), game))

        /** The last leg, the one that decided the match. */
        val game: X01Game
            get() = match.currentLeg
    }

    /**
     * The Cricket game screen (docs/rules/cricket.md, CR22 to CR25).
     *
     * @property journal the journal of the match, in step with [game] (DJ1, DJ4); saved with it.
     * @property isNewGame whether "Start" or "Rematch" opened this screen, rather than a resume (AN4, AN16, AN31).
     * @property noVoiceNotice the open "no offline voice" message (AN16), or `null`. Neither is saved.
     */
    data class CricketGame(
        val game: CricketGameScreenState,
        val journal: MatchJournal,
        val isNewGame: Boolean = false,
        val noVoiceNotice: NoVoiceNotice? = null,
    ) : Screen

    /**
     * Between two legs of a Cricket match (M16, CR26). [match] already has the leg confirmed and its next leg started,
     * with no dart; the winner detail is read from its last confirmed leg.
     *
     * @property journal the journal of the match, like [BetweenLegs.journal].
     * @property isAbandonDialogShown whether the "Abandon the game?" confirmation is open.
     */
    data class CricketBetweenLegs(
        val match: CricketMatch,
        val journal: MatchJournal,
        val isAbandonDialogShown: Boolean = false,
    ) : Screen

    /** End of a finished Cricket match (M17, CR27); a single game is a match of one leg. [match] is not confirmed. */
    data class CricketEnd(val match: CricketMatch) : Screen
}

/** Whether [this] is a game screen of any mode, the between-legs screens included (R1, R14; ADR 0002). */
val Screen.isGameOrBetweenLegs: Boolean
    get() = this is Screen.Game || this is Screen.BetweenLegs || this is Screen.CricketGame ||
        this is Screen.CricketBetweenLegs

/** Whether [this] is a game screen opened by "Start" or "Rematch", of any mode (AN4, AN16). */
val Screen.isNewGameShown: Boolean
    get() = (this is Screen.Game && isNewGame) || (this is Screen.CricketGame && isNewGame)

/**
 * Whole UI state of the app.
 *
 * @property lastSetup the shared roster and, per mode, the settings of the last game started in that mode (Start or
 *   Rematch), to prefill the next setup (rules R7, R13, R22); `null` before any game was started.
 */
data class AppState(
    val screen: Screen = Screen.Home(),
    val lastSetup: LastSetups? = null,
)

/**
 * What the ViewModel read from the clock and the id source for one event, handed to the reducer so that it stays pure
 * (review A4): the entry time of a dart, the start time of a match, and the id of the match that "Start" or "Rematch"
 * creates (DJ1, AR4, AR5).
 *
 * @property at milliseconds since the epoch.
 * @property matchId a new match id (lower-case UUID); only read by "Start" and "Rematch".
 */
data class EventStamp(val at: Long, val matchId: String)

/** Everything the user can do. An event that does not apply to the current screen is ignored. */
sealed interface AppEvent {
    /** System back. */
    data object Back : AppEvent

    /** "New game" of [mode] on the home and end screens (CR20). */
    data class NewGame(val mode: GameMode) : AppEvent

    /** "Resume game" on the home screen (rule R2). */
    data object ResumeTapped : AppEvent

    /** "Delete" in the new-game confirmation (rule R3). */
    data object NewGameConfirmed : AppEvent

    /** "Cancel" in the new-game confirmation, or a tap outside it (rule R3). */
    data object NewGameDismissed : AppEvent

    /** "OK" on the "saved game could not be resumed" message, or a tap outside it (rule R9). */
    data object RestoreFailedDismissed : AppEvent

    data class StartScoreSelected(val score: Int) : AppEvent

    data class CheckoutRuleSelected(val rule: CheckoutRule) : AppEvent

    /** Standard or Cut-throat in the Cricket setup (CR21). */
    data class CricketVariantSelected(val variant: CricketVariant) : AppEvent

    /** The "25-Bull" switch of the Cricket setup (CR21). */
    data class BullInPlayToggled(val isInPlay: Boolean) : AppEvent

    data class NameChanged(val index: Int, val name: String) : AppEvent

    /** The name field at [index] got the focus (PF3). */
    data class NameFieldFocused(val index: Int) : AppEvent

    /** The name field at [index] lost the focus (PF3). */
    data class NameFieldFocusLost(val index: Int) : AppEvent

    /** A tap on the profile suggestion [name] under the field at [index] (PF3). */
    data class SuggestionPicked(val index: Int, val name: String) : AppEvent

    /** The profiles read when the setup opened; an unreadable archive gives an empty list (PF3, AR9). */
    data class ProfilesLoaded(val profiles: List<Profile>) : AppEvent

    data object PlayerAdded : AppEvent

    /** The remove cross of the row at [index] (M13, PC3). */
    data class PlayerRemoved(val index: Int) : AppEvent

    data object PlayersShuffled : AppEvent

    /** − (delta -1) or + (delta 1) of "Legs per set" (M13). */
    data class LegsPerSetChanged(val delta: Int) : AppEvent

    /** − (delta -1) or + (delta 1) of "Sets" (M13). */
    data class SetsChanged(val delta: Int) : AppEvent

    data object StartRequested : AppEvent

    data class NumberTapped(val number: Int) : AppEvent

    /** Double or Triple key. */
    data class MultiplierTapped(val multiplier: Multiplier) : AppEvent

    /** A target button of the Cricket board: one tap, one [dart] (CR24). */
    data class CricketDartTapped(val dart: Dart) : AppEvent

    data object OuterBullTapped : AppEvent

    data object BullTapped : AppEvent

    data object MissTapped : AppEvent

    /** "Undo" of the action bar, in every zone mode (VT13, M15). */
    data object UndoTapped : AppEvent

    /** "Next player" of the action bar. */
    data object NextPlayerTapped : AppEvent

    /** "Confirm leg", "Confirm set" or "Confirm match" of the action bar, in the CHECKOUT zone (M15). */
    data object FinishGameTapped : AppEvent

    /** The archive of the confirmed match was written, or was already there (AR2, AR4): the End screen shows. */
    data object ArchiveSucceeded : AppEvent

    /** The archive could not be written (AR3): the zone stays, with a message and an enabled confirm button. */
    data object ArchiveFailed : AppEvent

    /** "Next leg" on the between-legs screen (M16). */
    data object NextLegTapped : AppEvent

    data object AbandonConfirmed : AppEvent

    data object AbandonDismissed : AppEvent

    data object Rematch : AppEvent

    /** The gear on the home screen (AN13). */
    data object SettingsTapped : AppEvent

    /** The "History" button of the home screen (HS1). */
    data object HistoryTapped : AppEvent

    /** The mode filter of History (HS3); `null` is "All". */
    data class HistoryModeSelected(val mode: ArchivedMode?) : AppEvent

    /** The player filter of History (HS3); a profile id, or `null` for "All". */
    data class HistoryProfileSelected(val profileId: Long?) : AppEvent

    /** "Show all matches" of History (HS4). */
    data object HistoryShowAllTapped : AppEvent

    /** "Retry" after a failed History read (HS7). */
    data object HistoryRetryTapped : AppEvent

    /** The list stopped scrolling at [index], [offset] pixels into it (HS6). */
    data class HistoryScrolled(val index: Int, val offset: Int) : AppEvent

    /** A tap on the row of the match of session [sessionId]; the detail screen opens (HS5). */
    data class HistoryMatchTapped(val sessionId: Long) : AppEvent

    /** The answer to read [loadId] of History (HS7); an answer to an older read is ignored. */
    data class HistoryLoaded(val loadId: Int, val result: DbResult<HistoryList>) : AppEvent

    /** "Retry" after a failed read of the match detail (HS7). */
    data object MatchDetailRetryTapped : AppEvent

    /** The answer to read [loadId] of the match detail (HS7); an answer to an older read is ignored. */
    data class MatchDetailLoaded(val loadId: Int, val result: DbResult<MatchDetail>) : AppEvent

    /** The game screen found no usable voice (AN16). The ViewModel decides whether the message shows. */
    data class VoiceUnavailable(val canInstall: Boolean) : AppEvent

    /** "OK" or back on the "no offline voice" message (AN16). */
    data object NoVoiceNoticeDismissed : AppEvent

    /** "Install a voice" on the "no offline voice" message: it also closes it (AN16, AN17). */
    data object InstallVoiceTapped : AppEvent
}
