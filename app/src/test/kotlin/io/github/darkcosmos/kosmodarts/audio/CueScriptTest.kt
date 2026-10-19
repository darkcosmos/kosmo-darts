package io.github.darkcosmos.kosmodarts.audio

import io.github.darkcosmos.kosmodarts.engine.x01.X01Cue
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every cue and its sounds and texts (docs/rules/announcements.md, AN8, AN9). */
class CueScriptTest {

    private fun of(cue: X01Cue) = CueScript.of(cue, TestPhrases)

    private fun steps(vararg steps: AudioStep) = AudioAction.Announcement(steps.toList())

    @Test
    fun `AN9 dart cues are dart sounds`() {
        assertEquals(AudioAction.DartSound(SoundEffect.DART_HIT), of(X01Cue.DartHit))
        assertEquals(AudioAction.DartSound(SoundEffect.DART_MISS), of(X01Cue.DartMiss))
    }

    @Test
    fun `AN23 the undo cue is an undo`() {
        assertEquals(AudioAction.Undo, of(X01Cue.DartUndone))
    }

    @Test
    fun `AN8 turn score and no score are spoken only`() {
        assertEquals(steps(AudioStep.Speech("score 60")), of(X01Cue.TurnScore(60)))
        assertEquals(steps(AudioStep.Speech("no score")), of(X01Cue.NoScore))
    }

    @Test
    fun `AN8 AN9 180 and bust are a sound then a voice`() {
        assertEquals(
            steps(AudioStep.Sound(SoundEffect.ONE_EIGHTY), AudioStep.Speech("180!")),
            of(X01Cue.OneEighty),
        )
        assertEquals(steps(AudioStep.Sound(SoundEffect.BUST), AudioStep.Speech("bust")), of(X01Cue.Bust))
    }

    @Test
    fun `AN8 AN9 wins are a sound then a voice with the name`() {
        assertEquals(
            steps(AudioStep.Sound(SoundEffect.GAME_SHOT), AudioStep.Speech("game shot Ann")),
            of(X01Cue.GameShot(0, "Ann")),
        )
        assertEquals(
            steps(AudioStep.Sound(SoundEffect.BIG_FISH), AudioStep.Speech("big fish Ann")),
            of(X01Cue.BigFish(0, "Ann")),
        )
    }

    @Test
    fun `AN8 turn start says the name, and the remaining score when a checkout is possible`() {
        assertEquals(steps(AudioStep.Speech("start Bob")), of(X01Cue.TurnStart(1, "Bob", null)))
        assertEquals(steps(AudioStep.Speech("start Bob requires 40")), of(X01Cue.TurnStart(1, "Bob", 40)))
    }
}
