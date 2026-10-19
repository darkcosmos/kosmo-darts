package io.github.darkcosmos.kosmodarts.settings

/** Where [AudioSettings] are kept. Main thread only. */
interface AudioSettingsStore {
    /** The stored settings, or the defaults for anything missing or invalid. Never throws. */
    fun read(): AudioSettings

    fun write(settings: AudioSettings)
}

/** Keeps the settings in memory only: the default for code that does not need them on the device. */
class InMemoryAudioSettingsStore(private var settings: AudioSettings = AudioSettings()) : AudioSettingsStore {
    override fun read(): AudioSettings = settings

    override fun write(settings: AudioSettings) {
        this.settings = settings
    }
}
