package io.github.darkcosmos.kosmodarts.audio

import io.github.darkcosmos.kosmodarts.audio.SoundEffect.BUST
import io.github.darkcosmos.kosmodarts.audio.SoundEffect.DART_CANCEL
import io.github.darkcosmos.kosmodarts.audio.SoundEffect.DART_HIT
import io.github.darkcosmos.kosmodarts.audio.SoundEffect.DART_MISS
import io.github.darkcosmos.kosmodarts.audio.SoundEffect.ONE_EIGHTY
import io.github.darkcosmos.kosmodarts.audio.SoundEffect.VICTORY
import io.github.darkcosmos.kosmodarts.engine.match.LegOutcome
import io.github.darkcosmos.kosmodarts.engine.x01.X01Cue
import io.github.darkcosmos.kosmodarts.settings.AudioSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Order, overlap, undo and switches of the sequencer (docs/rules/announcements.md, AN10, AN14, AN18 to AN23). */
class AnnouncementPlayerTest {

    private val timeline = Timeline()
    private val clock = VirtualScheduler()
    private val sounds = FakeSoundPlayer(timeline, clock)
    private val speaker = FakeSpeaker(timeline, clock)
    private var settings = AudioSettings()
    private val player = AnnouncementPlayer(sounds, speaker, clock, { settings })

    private fun dart(effect: SoundEffect = DART_HIT) = player.play(AudioAction.DartSound(effect))

    private fun announce(vararg steps: AudioStep) = player.play(AudioAction.Announcement(steps.toList()))

    private fun soundThenSpeech(effect: SoundEffect, text: String) =
        announce(AudioStep.Sound(effect), AudioStep.Speech(text))

    /** AN10: dart sound, then the event sound when it ends, then the voice when the event sound ends. */
    @Test
    fun `AN10 dart sound then event sound then voice`() {
        dart()
        soundThenSpeech(BUST, "bust")
        clock.advanceBy(10_000)

        assertEquals(
            listOf(Heard.Sound(DART_HIT, 0), Heard.Sound(BUST, 510), Heard.Speech("bust", 510 + 1_910)),
            timeline.heard,
        )
    }

    /** AN10: the turn start voice waits for the end-of-turn voice. */
    @Test
    fun `AN10 turn start voice waits for the end of turn voice`() {
        dart()
        announce(AudioStep.Speech("score 60"))
        announce(AudioStep.Speech("start Bob"))
        clock.advanceBy(10_000)

        assertEquals(
            listOf(Heard.Sound(DART_HIT, 0), Heard.Speech("score 60", 510), Heard.Speech("start Bob", 510 + 800)),
            timeline.heard,
        )
    }

    /** AN10: a turn start voice queues behind a long pending sequence (a 4 s sound, then a voice). */
    @Test
    fun `AN10 a turn start voice queues behind a long sequence`() {
        soundThenSpeech(ONE_EIGHTY, "score 3")
        announce(AudioStep.Speech("start Bob"))
        clock.advanceBy(20_000)

        assertEquals(
            listOf(
                Heard.Sound(ONE_EIGHTY, 0),
                Heard.Speech("score 3", 4_000),
                Heard.Speech("start Bob", 4_000 + 800),
            ),
            timeline.heard,
        )
    }

    /** AN10: a voice and an event sound never overlap, whatever the sequence. */
    @Test
    fun `AN10 a voice and an event sound never overlap`() {
        dart()
        soundThenSpeech(ONE_EIGHTY, "180!")
        announce(AudioStep.Speech("start Bob"))
        soundThenSpeech(BUST, "bust")
        clock.advanceBy(30_000)

        val intervals = timeline.heard.filter { it !is Heard.Sound || it.effect != DART_HIT }.map {
            val length = when (it) {
                is Heard.Sound -> FakeSoundPlayer.DEFAULT_DURATIONS.getValue(it.effect)
                is Heard.Speech -> 800L
            }
            it.at until it.at + length
        }
        intervals.zipWithNext().forEach { (first, second) ->
            assertTrue("$first overlaps $second", first.last < second.first)
        }
    }

    /** AN10: fast dart sounds play at once and may overlap; none is delayed. */
    @Test
    fun `AN10 fast dart sounds overlap and are never delayed`() {
        dart()
        clock.advanceBy(100)
        dart(DART_MISS)
        clock.advanceBy(100)
        dart()

        assertEquals(
            listOf(Heard.Sound(DART_HIT, 0), Heard.Sound(DART_MISS, 100), Heard.Sound(DART_HIT, 200)),
            timeline.heard,
        )
    }

    /** AN10: a dart sound that is not loaded yet is skipped, not played later. */
    @Test
    fun `AN10 a sound not loaded yet is skipped`() {
        sounds.notLoaded += DART_HIT
        dart()
        sounds.notLoaded.clear()
        clock.advanceBy(5_000)

        assertEquals(emptyList<Heard>(), timeline.heard)
    }

    /**
     * AN10: a dart sound entered while a voice plays is played at once and keeps the order of the queue; the next
     * step only starts once that dart sound has ended, so it never overlaps an event sound or a voice either.
     */
    @Test
    fun `AN10 a dart sound during a voice plays at once`() {
        announce(AudioStep.Speech("start Ann"), AudioStep.Speech("score 3"))
        clock.advanceBy(300)
        dart()
        clock.advanceBy(10_000)

        assertEquals(
            listOf(
                Heard.Speech("start Ann", 0),
                Heard.Sound(DART_HIT, 300),
                Heard.Speech("score 3", 300 + 510),
            ),
            timeline.heard,
        )
    }

    /** AN10: an event sound whose length is unknown lets the next step run after the default length. */
    @Test
    fun `AN10 an unknown sound length uses the default`() {
        val unknown = FakeSoundPlayer(timeline, clock, durations = emptyMap())
        val player = AnnouncementPlayer(unknown, speaker, clock, { settings })
        player.play(AudioAction.Announcement(listOf(AudioStep.Sound(BUST), AudioStep.Speech("bust"))))
        clock.advanceBy(5_000)

        assertEquals(
            listOf(Heard.Sound(BUST, 0), Heard.Speech("bust", AnnouncementPlayer.DEFAULT_SOUND_MILLIS)),
            timeline.heard,
        )
    }

    /** AN23: an undo stops everything in progress or pending, then plays the cancel sound. */
    @Test
    fun `AN23 undo stops everything then plays the cancel sound`() {
        dart()
        soundThenSpeech(BUST, "bust")
        announce(AudioStep.Speech("start Bob"))
        clock.advanceBy(600) // the bust sound plays
        player.play(AudioAction.Undo)
        clock.advanceBy(20_000)

        assertEquals(
            listOf(Heard.Sound(DART_HIT, 0), Heard.Sound(BUST, 510), Heard.Sound(DART_CANCEL, 600)),
            timeline.heard,
        )
        assertEquals(1, sounds.stopAllCount)
        assertEquals(1, speaker.stopCount)
    }

    /** AN23: an undo during a voice stops it; its late end does not start what was pending. */
    @Test
    fun `AN23 undo during a voice ignores its late end`() {
        announce(AudioStep.Speech("score 60"), AudioStep.Speech("start Bob"))
        clock.advanceBy(100)
        player.play(AudioAction.Undo)
        clock.advanceBy(20_000)

        assertEquals(listOf(Heard.Speech("score 60", 0), Heard.Sound(DART_CANCEL, 100)), timeline.heard)
        assertEquals(listOf(1), speaker.endCounts)
    }

    /** AN23: with sounds off, an undo stops everything and plays nothing. */
    @Test
    fun `AN23 undo with sounds off plays no cancel sound`() {
        settings = AudioSettings(soundsEnabled = false)
        announce(AudioStep.Speech("score 60"))
        player.play(AudioAction.Undo)
        clock.advanceBy(5_000)

        assertEquals(listOf(Heard.Speech("score 60", 0)), timeline.heard)
        assertEquals(1, speaker.stopCount)
    }

    /** AN14: voice off, nothing is spoken; sounds still play. */
    @Test
    fun `AN14 voice off speaks nothing`() {
        settings = AudioSettings(voiceEnabled = false)
        dart()
        soundThenSpeech(BUST, "bust")
        announce(AudioStep.Speech("start Bob"))
        clock.advanceBy(10_000)

        assertEquals(listOf(DART_HIT, BUST), timeline.sounds)
        assertEquals(emptyList<String>(), timeline.speech)
    }

    /** AN14: sounds off, no sound plays; the voice still speaks, without waiting. */
    @Test
    fun `AN14 sounds off plays nothing`() {
        settings = AudioSettings(soundsEnabled = false)
        dart()
        soundThenSpeech(BUST, "bust")
        clock.advanceBy(10_000)

        assertEquals(listOf(Heard.Speech("bust", 0)), timeline.heard)
    }

    /** AN14: both off, nothing at all. */
    @Test
    fun `AN14 both off plays and speaks nothing`() {
        settings = AudioSettings(voiceEnabled = false, soundsEnabled = false)
        dart()
        soundThenSpeech(BUST, "bust")
        player.play(AudioAction.Undo)
        clock.advanceBy(10_000)

        assertEquals(emptyList<Heard>(), timeline.heard)
    }

    /** AN20: a skipped utterance (focus refused) lets the next step run; sounds still play. */
    @Test
    fun `AN20 a skipped utterance does not block the queue`() {
        speaker.skipAll = true
        dart()
        soundThenSpeech(BUST, "bust")
        soundThenSpeech(ONE_EIGHTY, "180!")
        clock.advanceBy(20_000)

        assertEquals(listOf(DART_HIT, BUST, ONE_EIGHTY), timeline.sounds)
        assertEquals(emptyList<String>(), timeline.speech)
        assertEquals(listOf(1, 1), speaker.endCounts)
    }

    /** AN18: an utterance that fails ends once and the queue goes on; later sounds still play. */
    @Test
    fun `AN18 a failed utterance does not stall the queue`() {
        speaker.failNext = true
        soundThenSpeech(BUST, "bust")
        announce(AudioStep.Speech("start Bob"))
        dart()
        soundThenSpeech(ONE_EIGHTY, "180!")
        clock.advanceBy(20_000)

        assertEquals(listOf(BUST, DART_HIT, ONE_EIGHTY), timeline.sounds)
        assertEquals(emptyList<String>(), timeline.speech)
        assertEquals(listOf(1, 1, 1), speaker.endCounts)
    }

    private fun dartWithCall(call: String) = player.play(AudioAction.DartSound(DART_HIT, call))

    private fun callsOn() {
        settings = AudioSettings(callEachDart = true)
    }

    /** AN25: the call comes right after its impact sound. */
    @Test
    fun `AN25 a dart call follows its impact sound`() {
        callsOn()
        dartWithCall("Triple 20")
        clock.advanceBy(5_000)

        assertEquals(listOf(Heard.Sound(DART_HIT, 0), Heard.Speech("Triple 20", 510)), timeline.heard)
    }

    /** AN25, AN14: no call when "Call each dart" is off (the default) or when the voice is off. */
    @Test
    fun `AN25 no call when calls or the voice are off`() {
        dartWithCall("18")
        settings = AudioSettings(voiceEnabled = false, callEachDart = true)
        dartWithCall("19")
        clock.advanceBy(5_000)

        assertEquals(listOf(DART_HIT, DART_HIT), timeline.sounds)
        assertEquals(emptyList<String>(), timeline.speech)
    }

    /** AN25: a new dart removes the call that still waits for its impact sound. */
    @Test
    fun `AN25 a new dart drops a waiting call`() {
        callsOn()
        dartWithCall("18")
        clock.advanceBy(100)
        dartWithCall("Double 20")
        clock.advanceBy(5_000)

        assertEquals(listOf("Double 20"), timeline.speech)
    }

    /** AN25: a new dart interrupts the call being spoken; its late end does not move the queue twice. */
    @Test
    fun `AN25 a new dart interrupts a call being spoken`() {
        callsOn()
        dartWithCall("18")
        announce(AudioStep.Speech("score 98"))
        clock.advanceBy(600) // "18" is being spoken
        dartWithCall("Bull")
        clock.advanceBy(5_000)

        assertEquals(listOf("18", "Bull", "score 98"), timeline.speech)
        assertEquals(1, speaker.stopCount)
        assertEquals(listOf(1, 1, 1), speaker.endCounts)
    }

    /** AN25: a call never interrupts another announcement: it waits behind a turn start voice. */
    @Test
    fun `AN25 a call waits behind a turn start voice`() {
        callsOn()
        announce(AudioStep.Speech("turn Bob"))
        clock.advanceBy(100)
        dartWithCall("18")
        clock.advanceBy(5_000)

        assertEquals(
            listOf(Heard.Speech("turn Bob", 0), Heard.Sound(DART_HIT, 100), Heard.Speech("18", 800)),
            timeline.heard,
        )
        assertEquals(0, speaker.stopCount)
    }

    /** AN25: a call waiting behind another voice is dropped by the next dart. */
    @Test
    fun `AN25 a call waiting behind a voice is dropped by the next dart`() {
        callsOn()
        announce(AudioStep.Speech("turn Bob"))
        clock.advanceBy(100)
        dartWithCall("18")
        clock.advanceBy(100)
        dartWithCall("19")
        clock.advanceBy(5_000)

        assertEquals(listOf("turn Bob", "19"), timeline.speech)
    }

    private fun win() = announce(AudioStep.SoundWithSpeech(VICTORY, "win Ann"))

    /** AN10, AN25, A.2: on a checkout dart: impact, call, then the victory sound and the win voice together. */
    @Test
    fun `AN25 A2 the checkout dart is called before the victory sound and voice`() {
        callsOn()
        dartWithCall("Double 20")
        win()
        clock.advanceBy(20_000)

        assertEquals(
            listOf(
                Heard.Sound(DART_HIT, 0),
                Heard.Speech("Double 20", 510),
                Heard.Sound(VICTORY, 510 + 800),
                Heard.Speech("win Ann", 510 + 800),
            ),
            timeline.heard,
        )
    }

    /** AN25, A.1: on a 180 with calls on: the call of the last dart, then the 180 sound, and no score voice. */
    @Test
    fun `AN25 A1 a 180 is the call, then its sound, and no score voice`() {
        callsOn()
        dartWithCall("Triple 20")
        announce(AudioStep.Sound(ONE_EIGHTY))
        clock.advanceBy(20_000)

        assertEquals(
            listOf(Heard.Sound(DART_HIT, 0), Heard.Speech("Triple 20", 510), Heard.Sound(ONE_EIGHTY, 510 + 800)),
            timeline.heard,
        )
    }

    /** A.2: the victory sound and the win voice start together; the next step waits for the longer of the two. */
    @Test
    fun `A2 victory sound and voice start together and the next step waits for both`() {
        win()
        announce(AudioStep.Speech("first Bob"))
        clock.advanceBy(20_000)

        assertEquals(
            listOf(Heard.Sound(VICTORY, 0), Heard.Speech("win Ann", 0), Heard.Speech("first Bob", 4_000)),
            timeline.heard,
        )
    }

    /** A.2: a voice longer than the sound: the next step waits for the voice. */
    @Test
    fun `A2 the next step waits for a voice longer than the victory sound`() {
        val longVoice = FakeSpeaker(timeline, clock, speechMillis = 6_000)
        val player = AnnouncementPlayer(sounds, longVoice, clock, { settings })
        player.play(AudioAction.Announcement(listOf(AudioStep.SoundWithSpeech(VICTORY, "win Ann"))))
        player.play(AudioAction.Announcement(listOf(AudioStep.Speech("first Bob"))))
        clock.advanceBy(20_000)

        assertEquals(Heard.Speech("first Bob", 6_000), timeline.heard.last())
    }

    /** A.2, E2: sounds off: the win voice only; voice off: the victory sound only. */
    @Test
    fun `A2 each part of the win follows its own switch`() {
        settings = AudioSettings(soundsEnabled = false)
        announce(AudioStep.Sound(BUST))
        announce(AudioStep.Sound(ONE_EIGHTY))
        win()
        clock.advanceBy(20_000)
        assertEquals(listOf(Heard.Speech("win Ann", 0)), timeline.heard)

        timeline.heard.clear()
        settings = AudioSettings(voiceEnabled = false)
        win()
        announce(AudioStep.Speech("first Bob"))
        clock.advanceBy(20_000)
        assertEquals(listOf(Heard.Sound(VICTORY, 20_000)), timeline.heard)
    }

    private fun winCue(outcome: LegOutcome, bigFish: Boolean = false) = player.play(
        CueScript.of(
            if (bigFish) X01Cue.BigFish(0, "Ann", outcome) else X01Cue.GameShot(0, "Ann", outcome),
            TestPhrases,
        ),
    )

    /** AN27 (owner O1): a leg or set win, big fish included: no victory sound, its voice, and the next step waits. */
    @Test
    fun `AN27 a leg or set win plays its voice without the victory sound`() {
        listOf(
            Triple(LegOutcome.LEG, false, "leg Ann"),
            Triple(LegOutcome.LEG, true, "leg Ann"),
            Triple(LegOutcome.SET, false, "set Ann"),
            Triple(LegOutcome.SET, true, "set Ann"),
        ).forEach { (outcome, bigFish, voice) ->
            timeline.heard.clear()
            val start = clock.now()
            dart()
            winCue(outcome, bigFish)
            announce(AudioStep.Speech("first Bob"))
            clock.advanceBy(20_000)

            assertEquals(
                "$outcome big fish=$bigFish",
                listOf(
                    Heard.Sound(DART_HIT, start),
                    Heard.Speech(voice, start + 510),
                    Heard.Speech("first Bob", start + 510 + 800),
                ),
                timeline.heard,
            )
        }
    }

    /** AN27, AN9, AN10: the match win keeps the victory sound with its voice; the next step waits for both. */
    @Test
    fun `AN27 the match win plays the victory sound with its voice`() {
        dart()
        winCue(LegOutcome.MATCH)
        announce(AudioStep.Speech("first Bob"))
        clock.advanceBy(20_000)

        assertEquals(
            listOf(
                Heard.Sound(DART_HIT, 0),
                Heard.Sound(VICTORY, 510),
                Heard.Speech("win Ann", 510),
                Heard.Speech("first Bob", 510 + 4_000),
            ),
            timeline.heard,
        )
    }

    /** AN27: voice off and sounds on: nothing after the dart for a leg win, the victory sound for the match. */
    @Test
    fun `AN27 with the voice off a leg win is silent after the dart`() {
        settings = AudioSettings(voiceEnabled = false)
        dart()
        winCue(LegOutcome.LEG)
        clock.advanceBy(20_000)
        assertEquals(listOf(Heard.Sound(DART_HIT, 0)), timeline.heard)

        timeline.heard.clear()
        dart()
        winCue(LegOutcome.MATCH)
        clock.advanceBy(20_000)
        assertEquals(listOf(Heard.Sound(DART_HIT, 20_000), Heard.Sound(VICTORY, 20_000 + 510)), timeline.heard)
    }

    /** A.2: a victory sound not loaded yet: the voice still plays and the step ends with it. */
    @Test
    fun `A2 an unloaded victory sound leaves the voice`() {
        sounds.notLoaded += VICTORY
        win()
        announce(AudioStep.Speech("first Bob"))
        clock.advanceBy(20_000)

        assertEquals(listOf(Heard.Speech("win Ann", 0), Heard.Speech("first Bob", 800)), timeline.heard)
    }

    /** A.2: a victory sound of unknown length counts for the default length. */
    @Test
    fun `A2 an unknown victory sound length uses the default`() {
        val unknown = FakeSoundPlayer(timeline, clock, durations = emptyMap())
        val shortVoice = FakeSpeaker(timeline, clock, speechMillis = 100)
        val player = AnnouncementPlayer(unknown, shortVoice, clock, { settings })
        player.play(AudioAction.Announcement(listOf(AudioStep.SoundWithSpeech(VICTORY, "win Ann"))))
        player.play(AudioAction.Announcement(listOf(AudioStep.Speech("first Bob"))))
        clock.advanceBy(20_000)

        assertEquals(Heard.Speech("first Bob", AnnouncementPlayer.DEFAULT_SOUND_MILLIS), timeline.heard.last())
    }

    /** A.2, AN23: an undo during the win stops both its sound and its voice. */
    @Test
    fun `A2 undo stops the victory sound and voice`() {
        win()
        announce(AudioStep.Speech("first Bob"))
        clock.advanceBy(100)
        player.play(AudioAction.Undo)
        clock.advanceBy(20_000)

        assertEquals(
            listOf(Heard.Sound(VICTORY, 0), Heard.Speech("win Ann", 0), Heard.Sound(DART_CANCEL, 100)),
            timeline.heard,
        )
        assertEquals(1, sounds.stopAllCount)
        assertEquals(1, speaker.stopCount)
    }

    /** AN21: after stopAll, nothing scheduled plays any more. */
    @Test
    fun `AN21 stop all forgets everything pending`() {
        dart()
        soundThenSpeech(BUST, "score 3")
        player.stopAll()
        clock.advanceBy(20_000)

        assertEquals(listOf(Heard.Sound(DART_HIT, 0)), timeline.heard)
        assertEquals(0, clock.pendingCount)
    }
}
