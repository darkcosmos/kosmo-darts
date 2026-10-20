package io.github.darkcosmos.kosmodarts.audio

import io.github.darkcosmos.kosmodarts.engine.x01.X01Cue
import io.github.darkcosmos.kosmodarts.settings.AudioSettings
import io.github.darkcosmos.kosmodarts.ui.app.CueSink

/**
 * Sounds and voice of the game screen while it is shown (ADR 0004, decision 1). [speaker] is `null` when the voice
 * is off: the engine is then not even started.
 */
class GameAudioSession(
    private val sounds: SoundPlayer,
    private val speaker: Speaker?,
    scheduler: Scheduler,
    private val phrases: Phrases,
    settings: () -> AudioSettings,
) : CueSink {

    private val player = AnnouncementPlayer(sounds, speaker ?: SilentSpeaker, scheduler, settings)

    override fun onCues(cues: List<X01Cue>) = cues.forEach { player.play(CueScript.of(it, phrases)) }

    /** AN21: stops everything and frees the engine and the sounds. */
    fun release() {
        player.stopAll()
        speaker?.release()
        sounds.release()
    }
}

/** A [Speaker] that never speaks. */
object SilentSpeaker : Speaker {
    override val status: VoiceStatus = VoiceStatus.NO_USABLE_VOICE
    override var onStatusChanged: (VoiceStatus) -> Unit = {}
    override val enginePackage: String? = null

    override fun speak(text: String, onEnd: () -> Unit) = onEnd()

    override fun stop() = Unit

    override fun release() = Unit
}
