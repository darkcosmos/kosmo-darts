package io.github.darkcosmos.kosmodarts.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.darkcosmos.kosmodarts.engine.journal.Clock
import io.github.darkcosmos.kosmodarts.persistence.Persistence
import io.github.darkcosmos.kosmodarts.settings.AudioSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.random.Random

/**
 * Holds the [AppState] across configuration changes (rotation, language, font size). The game in progress and the
 * last setup survive process death through [onEffects], which writes them to the device (docs/rules/persistence.md).
 *
 * @param initial first state, built from the files found at launch (see [initialState]).
 * @param onEffects storage changes to make after each event, in order (see [persistenceEffects]).
 * @param audioSettings sound settings, read for the "no offline voice" message (AN16).
 * @param clock the entry time of each dart and the start time of each match (DJ1, AR5); the reducer reads no clock.
 * @param newMatchId the id of a new match (AR4), asked once for each "Start" and "Rematch" and for nothing else.
 */
class KosmoDartsViewModel(
    random: Random = Random.Default,
    initial: AppState = AppState(),
    private val audioSettings: AudioSettingsRepository,
    private val clock: Clock = Clock { System.currentTimeMillis() },
    private val newMatchId: () -> String = { UUID.randomUUID().toString() },
    private val onEffects: (List<PersistenceEffect>) -> Unit = {},
) : ViewModel() {

    private val reducer = AppReducer(random)
    private val mutableState = MutableStateFlow(initial)
    private val cueSinks = mutableListOf<CueSink>()

    val state: StateFlow<AppState> = mutableState.asStateFlow()

    /**
     * Whether the game screen or the between-legs screen is shown: the activity stores it for a return after process
     * death (rules R1, R14; ADR 0002).
     */
    val isOnGameScreen: Boolean
        get() = mutableState.value.screen.isGameOrBetweenLegs

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
        val after = reducer.reduce(before, event, stampFor(event))
        mutableState.value = after
        persistenceEffects(before, after).takeIf { it.isNotEmpty() }?.let(onEffects)
        if (after.screen::class != before.screen::class) onScreenChanged(after.screen)
        val cues = gameCues(before, event, after)
        if (cues.isNotEmpty()) cueSinks.toList().forEach { it.onCues(cues) }
        val cricketCues = cricketGameCues(before, event, after)
        if (cricketCues.isNotEmpty()) cueSinks.toList().forEach { it.onCricketCues(cricketCues) }
    }

    /** The clock reading, and a new match id only for the events that start a match (review A4). */
    private fun stampFor(event: AppEvent): EventStamp {
        val startsMatch = event == AppEvent.StartRequested || event == AppEvent.Rematch
        return EventStamp(clock.nowMillis(), if (startsMatch) newMatchId() else NO_MATCH_ID)
    }

    companion object {
        /** The id slot of an event that starts no match: the reducer never reads it. */
        private const val NO_MATCH_ID = ""

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
