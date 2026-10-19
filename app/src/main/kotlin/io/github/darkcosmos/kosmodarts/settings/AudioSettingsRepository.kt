package io.github.darkcosmos.kosmodarts.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The sound settings of the process, shared by the game and the Settings screen (ADR 0004). Reads [store] once.
 * Main thread only.
 */
class AudioSettingsRepository(private val store: AudioSettingsStore) {

    private val mutableSettings = MutableStateFlow(store.read())

    val settings: StateFlow<AudioSettings> = mutableSettings.asStateFlow()

    fun setVoiceEnabled(enabled: Boolean) = update { it.copy(voiceEnabled = enabled) }

    fun setSoundsEnabled(enabled: Boolean) = update { it.copy(soundsEnabled = enabled) }

    /** AN16: the "no offline voice" message is never shown again on this device. */
    fun markNoVoiceNoticeShown() = update { it.copy(noVoiceNoticeShown = true) }

    private fun update(change: (AudioSettings) -> AudioSettings) {
        val next = change(mutableSettings.value)
        if (next != mutableSettings.value) {
            mutableSettings.value = next
            store.write(next)
        }
    }
}
