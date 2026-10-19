package io.github.darkcosmos.kosmodarts.audio

import io.github.darkcosmos.kosmodarts.settings.AudioSettings

/**
 * Plays [AudioAction]s in the order of docs/rules/announcements.md, AN10, AN14, AN21, AN23 and AN25:
 * - a dart sound plays at once and may overlap other dart sounds;
 * - announcement steps run one at a time, from one queue, and each step starts only after the last dart sound has
 *   ended, so an event sound and a voice never overlap, except the victory sound and the win voice, which start
 *   together (A.2);
 * - a dart call goes to the head of the queue; the next dart drops it if it is still waiting, or interrupts it if it
 *   is being spoken. Other announcements are never interrupted by a dart;
 * - an undo stops everything, then plays the cancel sound.
 *
 * Main thread only.
 */
class AnnouncementPlayer(
    private val sounds: SoundPlayer,
    private val speaker: Speaker,
    private val scheduler: Scheduler,
    private val settings: () -> AudioSettings,
) {
    private val queue = ArrayDeque<Queued>()

    /** The step being played; only its own end moves the queue on, so a late end of an older step does nothing. */
    private var current: Running? = null
    private var dartSoundEndsAt = 0L

    fun play(action: AudioAction) {
        when (action) {
            is AudioAction.DartSound -> {
                playDartSound(action.effect)
                action.call?.let(::callDart)
            }
            AudioAction.Undo -> {
                stopAll()
                playDartSound(SoundEffect.DART_CANCEL)
            }
            is AudioAction.Announcement -> {
                queue.addAll(action.steps.map { Queued(it, isDartCall = false) })
                if (current == null) startNext()
            }
        }
    }

    /** AN21, AN23: stops what is playing, forgets what is pending. */
    fun stopAll() {
        queue.clear()
        cancelCurrent()
        dartSoundEndsAt = 0L
        speaker.stop()
        sounds.stopAll()
    }

    private fun playDartSound(effect: SoundEffect) {
        if (!settings().soundsEnabled || !sounds.play(effect)) return
        val endsAt = scheduler.now() + durationOf(effect)
        dartSoundEndsAt = maxOf(dartSoundEndsAt, endsAt)
    }

    /** AN25: the newest call replaces any older call, waiting or spoken; it never waits behind pending steps. */
    private fun callDart(text: String) {
        val audio = settings()
        if (!audio.voiceEnabled || !audio.callEachDart) return
        queue.removeAll { it.isDartCall }
        val running = current
        if (running != null && running.queued.isDartCall) {
            cancelCurrent()
            if (running.isSpeaking) speaker.stop()
        }
        queue.addFirst(Queued(AudioStep.Speech(text), isDartCall = true))
        if (current == null) startNext()
    }

    private fun cancelCurrent() {
        current?.scheduled?.cancel()
        current = null
    }

    private fun startNext() {
        val queued = queue.removeFirstOrNull() ?: return
        val running = Running(queued)
        current = running
        val wait = dartSoundEndsAt - scheduler.now()
        if (wait > 0) {
            running.scheduled = scheduler.post(wait) { if (current === running) run(running) }
        } else {
            run(running)
        }
    }

    private fun run(running: Running) {
        running.scheduled = null
        val audio = settings()
        when (val step = running.queued.step) {
            is AudioStep.Sound -> if (audio.soundsEnabled && sounds.play(step.effect)) {
                running.scheduled = scheduler.post(durationOf(step.effect)) { finish(running) }
            } else {
                finish(running)
            }
            is AudioStep.Speech -> if (audio.voiceEnabled) {
                running.isSpeaking = true
                speaker.speak(step.text) { finish(running) }
            } else {
                finish(running)
            }
            is AudioStep.SoundWithSpeech -> runTogether(running, step, audio)
        }
    }

    /** A.2: the sound and the speech start together; the step ends when both have ended. */
    private fun runTogether(running: Running, step: AudioStep.SoundWithSpeech, audio: AudioSettings) {
        val soundPlays = audio.soundsEnabled && sounds.play(step.effect)
        var partsLeft = (if (soundPlays) 1 else 0) + (if (audio.voiceEnabled) 1 else 0)
        val partEnded = {
            partsLeft--
            if (partsLeft == 0) finish(running)
        }
        if (partsLeft == 0) {
            finish(running)
            return
        }
        if (soundPlays) running.scheduled = scheduler.post(durationOf(step.effect)) { partEnded() }
        if (audio.voiceEnabled) {
            running.isSpeaking = true
            speaker.speak(step.text) { if (current === running) partEnded() }
        }
    }

    private fun finish(running: Running) {
        if (current !== running) return
        current = null
        startNext()
    }

    private fun durationOf(effect: SoundEffect): Long = sounds.durationMillis(effect) ?: DEFAULT_SOUND_MILLIS

    private class Queued(val step: AudioStep, val isDartCall: Boolean)

    private class Running(val queued: Queued) {
        var scheduled: Cancellable? = null
        var isSpeaking = false
    }

    companion object {
        /** Used while the length of a sound is not known yet. */
        const val DEFAULT_SOUND_MILLIS: Long = 1_000
    }
}
