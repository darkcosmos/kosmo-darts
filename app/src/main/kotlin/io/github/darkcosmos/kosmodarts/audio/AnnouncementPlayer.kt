package io.github.darkcosmos.kosmodarts.audio

import io.github.darkcosmos.kosmodarts.settings.AudioSettings

/**
 * Plays [AudioAction]s in the order of docs/rules/announcements.md, AN10, AN14, AN21 and AN23:
 * - a dart sound plays at once and may overlap other dart sounds;
 * - announcement steps run one at a time, from one queue, and the queue starts only after the last dart sound has
 *   ended, so an event sound and a voice never overlap;
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
    private val queue = ArrayDeque<AudioStep>()
    private var isBusy = false

    /** Incremented by [stopAll]: callbacks of an older generation do nothing. */
    private var generation = 0
    private var pendingStep: Cancellable? = null
    private var dartSoundEndsAt = 0L

    fun play(action: AudioAction) {
        when (action) {
            is AudioAction.DartSound -> playDartSound(action.effect)
            AudioAction.Undo -> {
                stopAll()
                playDartSound(SoundEffect.DART_CANCEL)
            }
            is AudioAction.Announcement -> enqueue(action.steps)
        }
    }

    /** AN21, AN23: stops what is playing, forgets what is pending. */
    fun stopAll() {
        generation++
        pendingStep?.cancel()
        pendingStep = null
        queue.clear()
        isBusy = false
        dartSoundEndsAt = 0L
        speaker.stop()
        sounds.stopAll()
    }

    private fun playDartSound(effect: SoundEffect) {
        if (!settings().soundsEnabled || !sounds.play(effect)) return
        val endsAt = scheduler.now() + durationOf(effect)
        dartSoundEndsAt = maxOf(dartSoundEndsAt, endsAt)
    }

    private fun enqueue(steps: List<AudioStep>) {
        queue.addAll(steps)
        if (!isBusy) startNext()
    }

    private fun startNext() {
        val step = queue.removeFirstOrNull()
        if (step == null) {
            isBusy = false
            return
        }
        isBusy = true
        val stepGeneration = generation
        val wait = dartSoundEndsAt - scheduler.now()
        if (wait > 0) {
            pendingStep = scheduler.post(wait) { if (stepGeneration == generation) run(step, stepGeneration) }
        } else {
            run(step, stepGeneration)
        }
    }

    private fun run(step: AudioStep, stepGeneration: Int) {
        pendingStep = null
        val current = settings()
        when (step) {
            is AudioStep.Sound -> if (current.soundsEnabled && sounds.play(step.effect)) {
                pendingStep = scheduler.post(durationOf(step.effect)) { continueAfter(stepGeneration) }
            } else {
                startNext()
            }
            is AudioStep.Speech -> if (current.voiceEnabled) {
                speaker.speak(step.text) { continueAfter(stepGeneration) }
            } else {
                startNext()
            }
        }
    }

    private fun continueAfter(stepGeneration: Int) {
        if (stepGeneration == generation) {
            pendingStep = null
            startNext()
        }
    }

    private fun durationOf(effect: SoundEffect): Long = sounds.durationMillis(effect) ?: DEFAULT_SOUND_MILLIS

    companion object {
        /** Used while the length of a sound is not known yet. */
        const val DEFAULT_SOUND_MILLIS: Long = 1_000
    }
}
