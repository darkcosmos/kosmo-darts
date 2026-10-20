package io.github.darkcosmos.kosmodarts.settings

import android.content.SharedPreferences

/**
 * [AudioSettingsStore] over a dedicated preferences file, apart from the saved game (AN15, ADR 0004). Reads are
 * validated by [AudioSettingsCodec]; a file the platform cannot read gives the defaults.
 */
class SharedPreferencesAudioSettingsStore(private val preferences: SharedPreferences) : AudioSettingsStore {

    override fun read(): AudioSettings {
        val values = try {
            preferences.all
        } catch (ignored: RuntimeException) {
            // ADR 0004: settings are never worth a crash; the defaults apply.
            emptyMap<String, Any?>()
        }
        return AudioSettingsCodec.decode(values)
    }

    override fun write(settings: AudioSettings) {
        val editor = preferences.edit().clear()
        AudioSettingsCodec.encode(settings).forEach { (key, value) ->
            when (value) {
                is Int -> editor.putInt(key, value)
                is Boolean -> editor.putBoolean(key, value)
            }
        }
        editor.apply()
    }

    companion object {
        /** Name of the preferences file, distinct from `saved_game` and `last_setup`. */
        const val FILE_NAME = "audio_settings"
    }
}
