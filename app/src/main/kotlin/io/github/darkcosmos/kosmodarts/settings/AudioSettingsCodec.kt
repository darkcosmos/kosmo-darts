package io.github.darkcosmos.kosmodarts.settings

/**
 * Maps [AudioSettings] to and from the key-value form of the settings file (ADR 0004, decision 2). Pure: the
 * decoder trusts nothing it reads and never throws.
 */
object AudioSettingsCodec {
    const val FORMAT_VERSION: Int = 1

    const val KEY_FORMAT_VERSION = "format_version"
    const val KEY_VOICE_ENABLED = "voice_enabled"
    const val KEY_SOUNDS_ENABLED = "sounds_enabled"
    const val KEY_NO_VOICE_NOTICE_SHOWN = "no_voice_notice_shown"

    /** Added with E4. Version 1 files without it read as `false`: a missing key gets its own default. */
    const val KEY_CALL_EACH_DART = "call_each_dart"

    /** Every value written, the format version included. */
    fun encode(settings: AudioSettings): Map<String, Any> = mapOf(
        KEY_FORMAT_VERSION to FORMAT_VERSION,
        KEY_VOICE_ENABLED to settings.voiceEnabled,
        KEY_SOUNDS_ENABLED to settings.soundsEnabled,
        KEY_NO_VOICE_NOTICE_SHOWN to settings.noVoiceNoticeShown,
        KEY_CALL_EACH_DART to settings.callEachDart,
    )

    /**
     * An unknown or missing [KEY_FORMAT_VERSION] gives the defaults; otherwise each missing or wrongly typed value
     * gets its own default. Unknown keys are ignored.
     */
    fun decode(values: Map<String, Any?>): AudioSettings {
        val defaults = AudioSettings()
        if (values[KEY_FORMAT_VERSION] != FORMAT_VERSION) return defaults
        return AudioSettings(
            voiceEnabled = values.booleanOr(KEY_VOICE_ENABLED, defaults.voiceEnabled),
            soundsEnabled = values.booleanOr(KEY_SOUNDS_ENABLED, defaults.soundsEnabled),
            noVoiceNoticeShown = values.booleanOr(KEY_NO_VOICE_NOTICE_SHOWN, defaults.noVoiceNoticeShown),
            callEachDart = values.booleanOr(KEY_CALL_EACH_DART, defaults.callEachDart),
        )
    }

    private fun Map<String, Any?>.booleanOr(key: String, default: Boolean): Boolean = this[key] as? Boolean ?: default
}
