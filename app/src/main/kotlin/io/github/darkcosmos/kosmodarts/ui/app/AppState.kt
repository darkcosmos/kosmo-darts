package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState

/** The screen on display, with its content. There is no navigation library: the screen is plain state. */
sealed interface Screen {
    data object Home : Screen

    data class Setup(val setup: SetupState) : Screen

    data class Game(val game: GameScreenState) : Screen

    /** End of a finished leg. */
    data class End(val game: X01Game) : Screen
}

/**
 * Whole UI state of the app.
 *
 * @property lastNames names of the last started game, to prefill the next setup. Kept for the app session only.
 */
data class AppState(
    val screen: Screen = Screen.Home,
    val lastNames: List<String> = emptyList(),
)

/** Everything the user can do. An event that does not apply to the current screen is ignored. */
sealed interface AppEvent {
    /** System back. */
    data object Back : AppEvent

    /** "New game" on the home and end screens. */
    data object NewGame : AppEvent

    data class StartScoreSelected(val score: Int) : AppEvent

    data class CheckoutRuleSelected(val rule: CheckoutRule) : AppEvent

    data class NameChanged(val index: Int, val name: String) : AppEvent

    data object PlayerAdded : AppEvent

    data object PlayerRemoved : AppEvent

    data object PlayersShuffled : AppEvent

    data object StartRequested : AppEvent

    data class NumberTapped(val number: Int) : AppEvent

    /** Double or Triple key. */
    data class MultiplierTapped(val multiplier: Multiplier) : AppEvent

    data object OuterBullTapped : AppEvent

    data object BullTapped : AppEvent

    data object MissTapped : AppEvent

    /** Undo key of the game screen, Undo in the checkout dialog, or "Undo last dart" on the end screen. */
    data object UndoTapped : AppEvent

    /** "Next player", next to the Undo key. */
    data object NextPlayerTapped : AppEvent

    /** "Finish game" in the checkout dialog. */
    data object FinishGameTapped : AppEvent

    data object AbandonConfirmed : AppEvent

    data object AbandonDismissed : AppEvent

    data object Rematch : AppEvent
}
