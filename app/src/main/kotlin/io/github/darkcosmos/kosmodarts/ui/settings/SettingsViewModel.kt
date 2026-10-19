package io.github.darkcosmos.kosmodarts.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.darkcosmos.kosmodarts.audio.Phrases
import io.github.darkcosmos.kosmodarts.audio.Speaker
import io.github.darkcosmos.kosmodarts.audio.VoiceStatus
import io.github.darkcosmos.kosmodarts.settings.AudioSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the Settings screen shows (AN13).
 *
 * @property canInstall whether "Install a voice" shows: no usable voice, and the engine can install one (AN17).
 */
data class SettingsUiState(
    val voiceEnabled: Boolean,
    val soundsEnabled: Boolean,
    val voiceStatus: VoiceStatus,
    val canInstall: Boolean,
)

/** Something the user does on the Settings screen. */
sealed interface SettingsEvent {
    data class VoiceToggled(val on: Boolean) : SettingsEvent

    data class SoundsToggled(val on: Boolean) : SettingsEvent

    data object InstallVoiceTapped : SettingsEvent
}

/**
 * The Settings screen's own ViewModel (AN13, AN14, AN17). The voice check runs on a [Speaker] probe that the activity
 * attaches while the screen is shown, and detaches (and frees) when it is left.
 */
class SettingsViewModel(
    private val repository: AudioSettingsRepository,
    private val phrases: () -> Phrases,
) : ViewModel() {

    private var probe: Speaker? = null
    private var canInstall: () -> Boolean = { false }
    private val mutableState = MutableStateFlow(stateOf(VoiceStatus.CHECKING))

    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    /** The screen is shown: [speaker] checks the voice again (AN17). */
    fun attachProbe(speaker: Speaker, canInstall: () -> Boolean) {
        probe = speaker
        this.canInstall = canInstall
        speaker.onStatusChanged = { if (probe === speaker) refresh() }
        refresh()
    }

    fun detachProbe() {
        probe?.onStatusChanged = {}
        probe = null
        canInstall = { false }
        refresh()
    }

    /** AN17: the engine's install screen could not be opened: "Install a voice" is hidden until the next check. */
    fun onInstallFailed() {
        canInstall = { false }
        refresh()
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.VoiceToggled -> onVoiceToggled(event.on)
            is SettingsEvent.SoundsToggled -> repository.setSoundsEnabled(event.on)
            // Opening the engine's screen is the activity's job; the state does not change.
            SettingsEvent.InstallVoiceTapped -> Unit
        }
        refresh()
    }

    /** AN14: "on" needs a usable voice, and then says so; otherwise the switch stays off. */
    private fun onVoiceToggled(on: Boolean) {
        val speaker = probe
        when {
            !on -> repository.setVoiceEnabled(false)
            speaker != null && speaker.status == VoiceStatus.AVAILABLE -> {
                repository.setVoiceEnabled(true)
                speaker.speak(phrases().announcementsOn()) {}
            }
            // Refused: the stored value stays off, so the switch snaps back (AN14).
            else -> Unit
        }
    }

    private fun refresh() {
        mutableState.value = stateOf(probe?.status ?: VoiceStatus.CHECKING)
    }

    private fun stateOf(status: VoiceStatus): SettingsUiState {
        val settings = repository.settings.value
        return SettingsUiState(
            voiceEnabled = settings.voiceEnabled,
            soundsEnabled = settings.soundsEnabled,
            voiceStatus = status,
            canInstall = status.isUnavailable && canInstall(),
        )
    }

    companion object {
        fun factory(repository: AudioSettingsRepository, phrases: () -> Phrases): ViewModelProvider.Factory =
            viewModelFactory { initializer { SettingsViewModel(repository, phrases) } }
    }
}
