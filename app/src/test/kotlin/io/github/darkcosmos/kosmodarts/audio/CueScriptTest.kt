package io.github.darkcosmos.kosmodarts.audio

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketCue
import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
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
    fun `A2 any win is the victory sound and the win phrase together`() {
        assertEquals(
            steps(AudioStep.SoundWithSpeech(SoundEffect.VICTORY, "win Ann")),
            of(X01Cue.GameShot(0, "Ann")),
        )
        assertEquals(
            steps(AudioStep.SoundWithSpeech(SoundEffect.VICTORY, "win Ann")),
            of(X01Cue.BigFish(0, "Ann")),
        )
    }

    /** AN27 (owner O1): a leg or set win is its own phrase alone, with no victory sound, big fish included. */
    @Test
    fun `AN27 a leg or set win is its phrase alone`() {
        assertEquals(steps(AudioStep.Speech("leg Ann")), of(X01Cue.GameShot(0, "Ann", LegOutcome.LEG)))
        assertEquals(steps(AudioStep.Speech("leg Ann")), of(X01Cue.BigFish(0, "Ann", LegOutcome.LEG)))
        assertEquals(steps(AudioStep.Speech("set Bob")), of(X01Cue.GameShot(1, "Bob", LegOutcome.SET)))
        assertEquals(steps(AudioStep.Speech("set Bob")), of(X01Cue.BigFish(1, "Bob", LegOutcome.SET)))
    }

    /** AN27: the match win, a single game included, keeps the victory sound with the victory phrase (A2). */
    @Test
    fun `AN27 the match win is the victory sound with the win phrase`() {
        listOf(X01Cue.GameShot(0, "Ann", LegOutcome.MATCH), X01Cue.BigFish(0, "Ann", LegOutcome.MATCH)).forEach {
            assertEquals(steps(AudioStep.SoundWithSpeech(SoundEffect.VICTORY, "win Ann")), of(it))
        }
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

    private fun ofCricket(cue: CricketCue) = CueScript.of(cue, TestPhrases)

    @Test
    fun `AN29 AN30 a Cricket hit and miss are the hit and miss sounds, the hit with its call`() {
        assertEquals(
            AudioAction.DartSound(SoundEffect.DART_HIT, "call 60"),
            ofCricket(CricketCue.DartHit(Dart.triple(20))),
        )
        assertEquals(AudioAction.DartSound(SoundEffect.DART_MISS, null), ofCricket(CricketCue.DartMiss))
    }

    @Test
    fun `AN30 a closed-sector hit plays the closed sector sound instead of the hit sound, with its call`() {
        assertEquals(
            AudioAction.DartSound(SoundEffect.CLOSED_SECTOR, "call 60"),
            ofCricket(CricketCue.ClosedSectorHit(Dart.triple(20))),
        )
    }

    @Test
    fun `AN23 AN29 the Cricket undo cue is an undo`() {
        assertEquals(AudioAction.Undo, ofCricket(CricketCue.DartUndone))
    }

    @Test
    fun `AN31 a Cricket turn start is the phrase without a checkout suffix`() {
        val first = CricketCue.TurnStart(1, "Bob", isGameStart = true)
        val next = CricketCue.TurnStart(1, "Bob", isGameStart = false)
        assertEquals(steps(AudioStep.Speech("first Bob")), ofCricket(first))
        assertEquals(steps(AudioStep.Speech("turn Bob")), ofCricket(next))
    }

    @Test
    fun `AN27 AN31 a Cricket win is the victory sound with the phrase for a match, the phrase alone otherwise`() {
        assertEquals(
            steps(AudioStep.SoundWithSpeech(SoundEffect.VICTORY, "win Ann")),
            ofCricket(CricketCue.Win(0, "Ann", LegOutcome.MATCH)),
        )
        assertEquals(steps(AudioStep.Speech("leg Ann")), ofCricket(CricketCue.Win(0, "Ann", LegOutcome.LEG)))
        assertEquals(steps(AudioStep.Speech("set Bob")), ofCricket(CricketCue.Win(1, "Bob", LegOutcome.SET)))
    }
}
