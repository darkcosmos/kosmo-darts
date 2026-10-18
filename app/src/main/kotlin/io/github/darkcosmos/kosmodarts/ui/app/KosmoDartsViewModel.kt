package io.github.darkcosmos.kosmodarts.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.darkcosmos.kosmodarts.persistence.Persistence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * Holds the [AppState] across configuration changes (rotation, language, font size). The game in progress and the
 * last setup survive process death through [onEffects], which writes them to the device (docs/rules/persistence.md).
 *
 * @param initial first state, built from the files found at launch (see [initialState]).
 * @param onEffects storage changes to make after each event, in order (see [persistenceEffects]).
 */
class KosmoDartsViewModel(
    random: Random = Random.Default,
    initial: AppState = AppState(),
    private val onEffects: (List<PersistenceEffect>) -> Unit = {},
) : ViewModel() {

    private val reducer = AppReducer(random)
    private val mutableState = MutableStateFlow(initial)

    val state: StateFlow<AppState> = mutableState.asStateFlow()

    /** Whether the game screen is shown: the activity stores it for a return after process death (rule R1). */
    val isOnGameScreen: Boolean
        get() = mutableState.value.screen is Screen.Game

    /** Called once per completed turn (see [completedTurn]). Does nothing yet. */
    var turnCompletedHook: TurnCompletedHook = TurnCompletedHook {}

    /** Events come from the main thread only. */
    fun onEvent(event: AppEvent) {
        val before = mutableState.value
        val after = reducer.reduce(before, event)
        mutableState.value = after
        persistenceEffects(before, after).takeIf { it.isNotEmpty() }?.let(onEffects)
        completedTurn(before, after)?.let(turnCompletedHook::onTurnCompleted)
    }

    companion object {
        /**
         * Builds the ViewModel from the files on the device. Runs once per ViewModel: on a new process or after the
         * activity was finished, never on a configuration change (ADR 0002, decision 3).
         */
        fun factory(persistence: Persistence, wasOnGameScreen: Boolean): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    KosmoDartsViewModel(
                        initial = initialState(persistence.load(), wasOnGameScreen),
                        onEffects = persistence::applyEffects,
                    )
                }
            }
    }
}
