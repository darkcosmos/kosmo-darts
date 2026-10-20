package io.github.darkcosmos.kosmodarts.settings

/** Where [AudioSettings] are kept. Main thread only. */
interface AudioSettingsStore {
    /** The stored settings, or the defaults for anything missing or invalid. Never throws. */
    fun read(): AudioSettings

    fun write(settings: AudioSettings)
}

