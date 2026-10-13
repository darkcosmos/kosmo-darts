package io.github.darkcosmos.kosmodarts.ui.app

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

/**
 * Holds the [AppState] across configuration changes (rotation, language, font size). Process death is not handled:
 * the state is lost if the system kills the app in the background.
 */
class KosmoDartsViewModel(random: Random = Random.Default) : ViewModel() {

    private val reducer = AppReducer(random)
    private val mutableState = MutableStateFlow(AppState())

    val state: StateFlow<AppState> = mutableState.asStateFlow()

    fun onEvent(event: AppEvent) {
        mutableState.update { reducer.reduce(it, event) }
    }
}
