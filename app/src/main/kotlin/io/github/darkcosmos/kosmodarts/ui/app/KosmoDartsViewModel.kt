package io.github.darkcosmos.kosmodarts.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.darkcosmos.kosmodarts.persistence.Persistence
import io.github.darkcosmos.kosmodarts.settings.AudioSettingsRepository
import io.github.darkcosmos.kosmodarts.settings.InMemoryAudioSettingsStore
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
 * @param audioSettings sound settings, read for the "no offline voice" message (AN16).
 */
class KosmoDartsViewModel(
    random: Random = Random.Default,
    initial: AppState = AppState(),
    private val audioSettings: AudioSettingsRepository = AudioSettingsRepository(InMemoryAudioSettingsStore()),
    private val onEffects: (List<PersistenceEffect>) -> Unit = {},
) : ViewModel() {

    private val reducer = AppReducer(random)
    private val mutableState = MutableStateFlow(initial)
    private val cueSinks = mutableListOf<CueSink>()

    val state: StateFlow<AppState> = mutableState.asStateFlow()

    /** Whether the game screen is shown: the activity stores it for a return after process death (rule R1). */
    val isOnGameScreen: Boolean
        get() = mutableState.value.screen is Screen.Game

    /** Called after an event changed the screen, before its cues are handed over (ADR 0004, decision 1). */
    var onScreenChanged: (Screen) -> Unit = {}

    /** Every [CueSink] receives the cues of each later event, once (AN7). */
    fun addCueSink(sink: CueSink) {
        cueSinks += sink
    }

    fun removeCueSink(sink: CueSink) {
        cueSinks -= sink
    }

    /** Events come from the main thread only. */
    fun onEvent(event: AppEvent) {
        val before = mutableState.value
        if (event is AppEvent.VoiceUnavailable) {
            if (!shouldShowNoVoiceNotice(before.screen, audioSettings.settings.value)) return
            // AN16: stored before the message opens, so that it never shows twice.
            audioSettings.markNoVoiceNoticeShown()
        }
        val after = reducer.reduce(before, event)
        mutableState.value = after
        persistenceEffects(before, after).takeIf { it.isNotEmpty() }?.let(onEffects)
        if (after.screen::class != before.screen::class) onScreenChanged(after.screen)
        val cues = gameCues(before, event, after)
        if (cues.isNotEmpty()) cueSinks.toList().forEach { it.onCues(cues) }
    }

    companion object {
        /**
         * Builds the ViewModel from the files on the device. Runs once per ViewModel: on a new process or after the
         * activity was finished, never on a configuration change (ADR 0002, decision 3).
         */
        fun factory(
            persistence: Persistence,
            audioSettings: AudioSettingsRepository,
            wasOnGameScreen: Boolean,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                KosmoDartsViewModel(
                    initial = initialState(persistence.load(), wasOnGameScreen),
                    audioSettings = audioSettings,
                    onEffects = persistence::applyEffects,
                )
            }
        }
    }
}
