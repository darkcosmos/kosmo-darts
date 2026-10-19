package io.github.darkcosmos.kosmodarts.audio

/** Whether the voice can speak (AN12, AN16, AN17). */
enum class VoiceStatus {
    /** The TTS engine is starting; nothing has been sent to it. */
    CHECKING,

    /** A usable embedded voice of the app language is set on the engine. */
    AVAILABLE,

    /** The engine works but has no usable voice of the app language, or its voice failed (AN18). */
    NO_USABLE_VOICE,

    /** There is no TTS engine, or it failed to start. */
    NO_ENGINE,
    ;

    /** Whether the voice is known to be unusable. */
    val isUnavailable: Boolean
        get() = this == NO_USABLE_VOICE || this == NO_ENGINE
}
