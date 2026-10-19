package io.github.darkcosmos.kosmodarts.audio

import android.content.Context
import org.robolectric.shadows.ShadowTextToSpeech

/**
 * Real text-to-speech (over Robolectric's [ShadowTextToSpeech]) and recording sound players, on one [timeline]
 * driven by the main looper's clock.
 */
internal class TestAudioFactory : AudioFactory {
    val timeline = Timeline()
    private val clock = HandlerScheduler()
    val soundPlayers = mutableListOf<FakeSoundPlayer>()
    val speakers = mutableListOf<Speaker>()

    /** The TTS engine package the speakers report; Robolectric has no default engine. */
    var enginePackage: String? = null

    override fun soundPlayer(context: Context): SoundPlayer =
        FakeSoundPlayer(timeline, clock).also { soundPlayers += it }

    override fun speaker(context: Context): Speaker =
        RecordingSpeaker(SystemAudioFactory().speaker(context), timeline, clock, enginePackage).also { speakers += it }

    override fun scheduler(): Scheduler = clock
}

/** Adds what the engine really speaks to the [timeline], in order with the sounds. */
internal class RecordingSpeaker(
    private val delegate: Speaker,
    private val timeline: Timeline,
    private val clock: Scheduler,
    override val enginePackage: String?,
) : Speaker by delegate {
    private val engine = ShadowTextToSpeech.getLastTextToSpeechInstance()

    override fun speak(text: String, onEnd: () -> Unit) {
        val before = spoken().size
        delegate.speak(text, onEnd)
        spoken().drop(before).forEach { timeline.heard += Heard.Speech(it, clock.now()) }
    }

    private fun spoken(): List<String> =
        engine?.let { org.robolectric.Shadows.shadowOf(it).spokenTextList }.orEmpty()
}
