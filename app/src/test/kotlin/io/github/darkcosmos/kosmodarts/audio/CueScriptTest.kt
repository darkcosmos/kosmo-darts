package io.github.darkcosmos.kosmodarts.audio

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Cue
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every cue and its sounds and texts (docs/rules/announcements.md, AN8, AN9, AN25, E2). */
class CueScriptTest {

    private fun of(cue: X01Cue) = CueScript.of(cue, TestPhrases)

    private fun steps(vararg steps: AudioStep) = AudioAction.Announcement(steps.toList())

    @Test
    fun `AN9 AN25 a hit is its sound with its call`() {
        assertEquals(AudioAction.DartSound(SoundEffect.DART_HIT, "call 60"), of(X01Cue.DartHit(Dart.triple(20))))
    }

    @Test
    fun `AN25 a miss is its sound without a call`() {
        assertEquals(AudioAction.DartSound(SoundEffect.DART_MISS, null), of(X01Cue.DartMiss))
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
    fun `E2 180 and bust are their sound only, no voice`() {
        assertEquals(steps(AudioStep.Sound(SoundEffect.ONE_EIGHTY)), of(X01Cue.OneEighty))
        assertEquals(steps(AudioStep.Sound(SoundEffect.BUST)), of(X01Cue.Bust))
    }

    @Test
    fun `E2 wins are their sound then the same win phrase`() {
        assertEquals(
            steps(AudioStep.Sound(SoundEffect.GAME_SHOT), AudioStep.Speech("win Ann")),
            of(X01Cue.GameShot(0, "Ann")),
        )
        assertEquals(
            steps(AudioStep.Sound(SoundEffect.BIG_FISH), AudioStep.Speech("win Ann")),
            of(X01Cue.BigFish(0, "Ann")),
        )
    }

    @Test
    fun `E1 turn start uses the game start or next turn line, then the remaining score`() {
        assertEquals(steps(AudioStep.Speech("first Bob")), of(X01Cue.TurnStart(1, "Bob", null, isGameStart = true)))
        assertEquals(steps(AudioStep.Speech("turn Bob")), of(X01Cue.TurnStart(1, "Bob", null, isGameStart = false)))
        assertEquals(
            steps(AudioStep.Speech("turn Bob requires 40")),
            of(X01Cue.TurnStart(1, "Bob", 40, isGameStart = false)),
        )
    }
}
