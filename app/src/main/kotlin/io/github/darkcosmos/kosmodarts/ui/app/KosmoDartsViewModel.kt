package io.github.darkcosmos.kosmodarts.ui.app

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * Holds the [AppState] across configuration changes (rotation, language, font size). Process death is not handled:
 * the state is lost if the system kills the app in the background.
 */
class KosmoDartsViewModel(random: Random = Random.Default) : ViewModel() {

    private val reducer = AppReducer(random)
    private val mutableState = MutableStateFlow(AppState())

    val state: StateFlow<AppState> = mutableState.asStateFlow()

    /** Called once per completed turn (see [completedTurn]). Does nothing yet. */
    var turnCompletedHook: TurnCompletedHook = TurnCompletedHook {}

    /** Events come from the main thread only. */
    fun onEvent(event: AppEvent) {
        val before = mutableState.value
        val after = reducer.reduce(before, event)
        mutableState.value = after
        completedTurn(before, after)?.let(turnCompletedHook::onTurnCompleted)
    }
}
