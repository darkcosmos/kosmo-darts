package io.github.darkcosmos.kosmodarts.audio

import io.github.darkcosmos.kosmodarts.engine.x01.X01Cue

/** One step of an announcement, played after the previous one has ended (AN10). */
sealed interface AudioStep {
    data class Sound(val effect: SoundEffect) : AudioStep

    data class Speech(val text: String) : AudioStep

    /** A.2: [effect] and [text] start together; the step ends when both have ended. */
    data class SoundWithSpeech(val effect: SoundEffect, val text: String) : AudioStep
}

/** What to do for one cue. */
sealed interface AudioAction {
    /**
     * AN10: played at once, may overlap other dart sounds, never queued. [call] is the dart call (AN25), spoken after
     * it when "Call each dart" is on.
     */
    data class DartSound(val effect: SoundEffect, val call: String? = null) : AudioAction

    /** AN23: stop everything, then play the cancel sound. */
    data object Undo : AudioAction

    /** AN10: steps queued after the dart sound and after the announcement in progress or pending. */
    data class Announcement(val steps: List<AudioStep>) : AudioAction
}

/**
 * Renders a cue as sounds and speech (AN8, AN9, AN25). Pure. Bust and 180 are their sound only: the sound replaces
 * the score (E2, A.1). A win is the victory sound and the win phrase together (A.2).
 */
object CueScript {

    fun of(cue: X01Cue, phrases: Phrases): AudioAction = when (cue) {
        is X01Cue.DartHit -> AudioAction.DartSound(SoundEffect.DART_HIT, phrases.dartCall(cue.dart))
        X01Cue.DartMiss -> AudioAction.DartSound(SoundEffect.DART_MISS)
        X01Cue.DartUndone -> AudioAction.Undo
        is X01Cue.TurnScore -> speech(phrases.turnScore(cue.points))
        X01Cue.NoScore -> speech(phrases.noScore())
        X01Cue.OneEighty -> sound(SoundEffect.ONE_EIGHTY)
        X01Cue.Bust -> sound(SoundEffect.BUST)
        is X01Cue.GameShot -> victory(phrases.win(cue.name))
        is X01Cue.BigFish -> victory(phrases.win(cue.name))
        is X01Cue.TurnStart -> speech(turnStart(cue, phrases))
    }

    /** E1: the turn start phrase, then the remaining score when a checkout is possible. */
    private fun turnStart(cue: X01Cue.TurnStart, phrases: Phrases): String {
        val phrase = phrases.turnStart(cue.name, cue.isGameStart)
        return cue.checkoutRemaining?.let { "$phrase ${phrases.checkoutSuffix(it)}" } ?: phrase
    }

    private fun speech(text: String) = AudioAction.Announcement(listOf(AudioStep.Speech(text)))

    private fun sound(effect: SoundEffect) = AudioAction.Announcement(listOf(AudioStep.Sound(effect)))

    private fun victory(text: String) =
        AudioAction.Announcement(listOf(AudioStep.SoundWithSpeech(SoundEffect.VICTORY, text)))
}
