package io.github.darkcosmos.kosmodarts.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** AN14, AN15: switches are independent and every change is stored. */
class AudioSettingsRepositoryTest {

    private class CountingStore : AudioSettingsStore {
        var stored = AudioSettings()
        val writes = mutableListOf<AudioSettings>()

        override fun read() = stored

        override fun write(settings: AudioSettings) {
            stored = settings
            writes += settings
        }
    }

    @Test
    fun `AN14 the switches are independent and each change is stored`() {
        val store = CountingStore()
        val repository = AudioSettingsRepository(store)

        repository.setSoundsEnabled(false)
        repository.setVoiceEnabled(false)
        repository.setSoundsEnabled(true)

        assertEquals(AudioSettings(voiceEnabled = false, soundsEnabled = true), repository.settings.value)
        assertEquals(
            listOf(
                AudioSettings(soundsEnabled = false),
                AudioSettings(voiceEnabled = false, soundsEnabled = false),
                AudioSettings(voiceEnabled = false, soundsEnabled = true),
            ),
            store.writes,
        )
    }

    @Test
    fun `AN15 a value that does not change is not written again`() {
        val store = CountingStore()
        val repository = AudioSettingsRepository(store)

        repository.setVoiceEnabled(true)
        repository.markNoVoiceNoticeShown()
        repository.markNoVoiceNoticeShown()

        assertEquals(listOf(AudioSettings(noVoiceNoticeShown = true)), store.writes)
    }

    @Test
    fun `AN15 the stored settings are read at creation`() {
        val store = CountingStore().apply { stored = AudioSettings(soundsEnabled = false) }
        assertEquals(AudioSettings(soundsEnabled = false), AudioSettingsRepository(store).settings.value)
    }
}
