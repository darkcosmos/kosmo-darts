package io.github.darkcosmos.kosmodarts.ui.settings

import io.github.darkcosmos.kosmodarts.audio.FakeSpeaker
import io.github.darkcosmos.kosmodarts.audio.TestPhrases
import io.github.darkcosmos.kosmodarts.audio.Timeline
import io.github.darkcosmos.kosmodarts.audio.VirtualScheduler
import io.github.darkcosmos.kosmodarts.audio.VoiceStatus
import io.github.darkcosmos.kosmodarts.settings.AudioSettings
import io.github.darkcosmos.kosmodarts.settings.AudioSettingsRepository
import io.github.darkcosmos.kosmodarts.settings.InMemoryAudioSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Settings screen's switches and voice status (docs/rules/announcements.md, AN13, AN14, AN17). */
class SettingsViewModelTest {

    private val store = InMemoryAudioSettingsStore()
    private val repository = AudioSettingsRepository(store)
    private val timeline = Timeline()
    private val clock = VirtualScheduler()
    private val viewModel = SettingsViewModel(repository) { TestPhrases }

    private fun probe(status: VoiceStatus) = FakeSpeaker(timeline, clock, status)

    private val state: SettingsUiState
        get() = viewModel.state.value

    @Test
    fun `AN14 both switches are on by default`() {
        assertTrue(state.voiceEnabled)
        assertTrue(state.soundsEnabled)
    }

    @Test
    fun `AN13 without a probe, the voice is being checked`() {
        assertEquals(VoiceStatus.CHECKING, state.voiceStatus)
        assertFalse(state.canInstall)
    }

    @Test
    fun `AN14 the switches are independent`() {
        viewModel.attachProbe(probe(VoiceStatus.AVAILABLE)) { false }
        viewModel.onEvent(SettingsEvent.SoundsToggled(false))
        assertTrue(state.voiceEnabled)
        assertFalse(state.soundsEnabled)

        viewModel.onEvent(SettingsEvent.VoiceToggled(false))
        viewModel.onEvent(SettingsEvent.SoundsToggled(true))
        assertFalse(state.voiceEnabled)
        assertTrue(state.soundsEnabled)
        assertEquals(AudioSettings(voiceEnabled = false, soundsEnabled = true), store.read())
    }

    @Test
    fun `AN14 voice on with a usable voice is stored and says announcements on`() {
        viewModel.attachProbe(probe(VoiceStatus.AVAILABLE)) { false }
        viewModel.onEvent(SettingsEvent.VoiceToggled(false))
        viewModel.onEvent(SettingsEvent.VoiceToggled(true))

        assertTrue(state.voiceEnabled)
        assertTrue(store.read().voiceEnabled)
        assertEquals(listOf("announcements on"), timeline.speech)
    }

    @Test
    fun `AN14 voice on without a usable voice snaps back, says nothing and stores nothing`() {
        VoiceStatus.entries.filter { it != VoiceStatus.AVAILABLE }.forEach { status ->
            val viewModel = SettingsViewModel(AudioSettingsRepository(InMemoryAudioSettingsStore())) { TestPhrases }
            viewModel.attachProbe(probe(status)) { true }
            viewModel.onEvent(SettingsEvent.VoiceToggled(false))
            viewModel.onEvent(SettingsEvent.VoiceToggled(true))

            assertFalse("$status", viewModel.state.value.voiceEnabled)
        }
        assertEquals(emptyList<String>(), timeline.speech)
    }

    @Test
    fun `AN14 voice on before any probe is refused`() {
        viewModel.onEvent(SettingsEvent.VoiceToggled(false))
        viewModel.onEvent(SettingsEvent.VoiceToggled(true))
        assertFalse(state.voiceEnabled)
    }

    @Test
    fun `AN14 voice off says nothing`() {
        viewModel.attachProbe(probe(VoiceStatus.AVAILABLE)) { false }
        viewModel.onEvent(SettingsEvent.VoiceToggled(false))
        assertEquals(emptyList<String>(), timeline.speech)
    }

    @Test
    fun `AN17 install shows only without a usable voice and when the engine can install`() {
        viewModel.attachProbe(probe(VoiceStatus.NO_USABLE_VOICE)) { true }
        assertTrue(state.canInstall)
        viewModel.attachProbe(probe(VoiceStatus.NO_USABLE_VOICE)) { false }
        assertFalse(state.canInstall)
        viewModel.attachProbe(probe(VoiceStatus.AVAILABLE)) { true }
        assertFalse(state.canInstall)
    }

    @Test
    fun `AN17 the status follows the probe and a failed install hides the button`() {
        val speaker = probe(VoiceStatus.CHECKING)
        viewModel.attachProbe(speaker) { true }
        assertEquals(VoiceStatus.CHECKING, state.voiceStatus)

        speaker.changeStatus(VoiceStatus.NO_USABLE_VOICE)
        assertEquals(VoiceStatus.NO_USABLE_VOICE, state.voiceStatus)
        assertTrue(state.canInstall)

        viewModel.onInstallFailed()
        assertFalse(state.canInstall)
    }

    @Test
    fun `AN17 a detached probe no longer changes the state`() {
        val speaker = probe(VoiceStatus.CHECKING)
        viewModel.attachProbe(speaker) { true }
        viewModel.detachProbe()
        speaker.changeStatus(VoiceStatus.AVAILABLE)

        assertEquals(VoiceStatus.CHECKING, state.voiceStatus)
    }
}
