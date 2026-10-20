package io.github.darkcosmos.kosmodarts.settings

/** Keeps the settings in memory only, for tests that do not need them on the device. */
class InMemoryAudioSettingsStore(private var settings: AudioSettings = AudioSettings()) : AudioSettingsStore {
    override fun read(): AudioSettings = settings

    override fun write(settings: AudioSettings) {
        this.settings = settings
    }
}

/** Default sound settings (voice and sounds on), in memory. */
fun inMemoryAudioSettings(settings: AudioSettings = AudioSettings()) =
    AudioSettingsRepository(InMemoryAudioSettingsStore(settings))
