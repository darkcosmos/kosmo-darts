package io.github.darkcosmos.kosmodarts.audio

import io.github.darkcosmos.kosmodarts.engine.x01.X01Cue

/** One step of an announcement, played after the previous one has ended (AN10). */
sealed interface AudioStep {
    data class Sound(val effect: SoundEffect) : AudioStep

    data class Speech(val text: String) : AudioStep
}

/** What to do for one cue. */
sealed interface AudioAction {
    /** AN10: played at once, may overlap other dart sounds, never queued. */
    data class DartSound(val effect: SoundEffect) : AudioAction

    /** AN23: stop everything, then play the cancel sound. */
    data object Undo : AudioAction

    /** AN10: steps queued after the dart sound and after the announcement in progress or pending. */
    data class Announcement(val steps: List<AudioStep>) : AudioAction
}

/** Renders a cue as sounds and speech (AN8, AN9). Pure. */
object CueScript {

    fun of(cue: X01Cue, phrases: Phrases): AudioAction = when (cue) {
        X01Cue.DartHit -> AudioAction.DartSound(SoundEffect.DART_HIT)
        X01Cue.DartMiss -> AudioAction.DartSound(SoundEffect.DART_MISS)
        X01Cue.DartUndone -> AudioAction.Undo
        is X01Cue.TurnScore -> speech(phrases.turnScore(cue.points))
        X01Cue.NoScore -> speech(phrases.noScore())
        X01Cue.OneEighty -> soundThenSpeech(SoundEffect.ONE_EIGHTY, phrases.oneEighty())
        X01Cue.Bust -> soundThenSpeech(SoundEffect.BUST, phrases.bust())
        is X01Cue.GameShot -> soundThenSpeech(SoundEffect.GAME_SHOT, phrases.gameShot(cue.name))
        is X01Cue.BigFish -> soundThenSpeech(SoundEffect.BIG_FISH, phrases.bigFish(cue.name))
        is X01Cue.TurnStart -> speech(
            cue.checkoutRemaining?.let { phrases.turnStartWithCheckout(cue.name, it) } ?: phrases.turnStart(cue.name),
        )
    }

    private fun speech(text: String) = AudioAction.Announcement(listOf(AudioStep.Speech(text)))

    private fun soundThenSpeech(effect: SoundEffect, text: String) =
        AudioAction.Announcement(listOf(AudioStep.Sound(effect), AudioStep.Speech(text)))
}
