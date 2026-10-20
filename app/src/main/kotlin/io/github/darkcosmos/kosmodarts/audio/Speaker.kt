package io.github.darkcosmos.kosmodarts.audio

/** Speaks announcements. Replaced by a fake in tests. Main thread only. */
interface Speaker {
    val status: VoiceStatus

    /** Called on the main thread each time [status] changes. */
    var onStatusChanged: (VoiceStatus) -> Unit

    /** Package of the TTS engine, for "Install a voice" (AN17), or `null` when unknown. */
    val enginePackage: String?

    /**
     * Speaks [text]. [onEnd] is called exactly once, on the main thread: after the utterance, on an error, on [stop],
     * or at once when the utterance is skipped (no usable voice, audio focus refused, released).
     */
    fun speak(text: String, onEnd: () -> Unit)

    /** Stops the utterance in progress or held; their [speak] callbacks are called. */
    fun stop()

    /** Stops and frees the engine. */
    fun release()
}
