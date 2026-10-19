package io.github.darkcosmos.kosmodarts.ui.announce

import android.media.AudioManager
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.audio.Heard
import io.github.darkcosmos.kosmodarts.audio.SoundEffect
import io.github.darkcosmos.kosmodarts.audio.TestAudioFactory
import io.github.darkcosmos.kosmodarts.audio.TtsTestSupport
import io.github.darkcosmos.kosmodarts.audio.TtsTestSupport.initLastEngine
import io.github.darkcosmos.kosmodarts.audio.TtsTestSupport.lastEngine
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.game.KeypadTags
import io.github.darkcosmos.kosmodarts.ui.resume.AppProcess
import io.github.darkcosmos.kosmodarts.ui.setup.SetupTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech
import java.time.Duration

/**
 * Voice and sounds during real games, through [io.github.darkcosmos.kosmodarts.MainActivity]
 * (docs/rules/announcements.md). The engine is Robolectric's text-to-speech; sounds are recorded by a fake player.
 */
internal class AnnouncementsScreenTest : AnnouncementsScreenTestBase() {
    /** AN8, E1, E2: every English announcement of a game, in order; bust and 180 have no voice. */
    @Test
    @Config(qualifiers = "en-rUS")
    fun `AN8 English announcements`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        playEveryAnnouncement()

        assertEquals("Ann, you're throwing first.", audio.allSpoken.first())
        assertEquals(
            listOf(
                "Ann, your turn. You require 121.",
                "No score",
                "Ann, you're up. You require 121.",
                "Ann, your turn. You require 121.",
                "94",
                "Ann, you're up. You require 27.",
                "Game shot! Ann wins",
                "Ann to throw first.",
                "131",
                "Ann, your turn. You require 170.",
                "Game shot! Ann wins",
            ),
            audio.timeline.speech,
        )
        assertEquals(listOf(SoundEffect.GAME_SHOT, SoundEffect.BIG_FISH), sounds.filter { it in winSounds })
    }

    /** AN8, E1, E2: the same game in French; the voice says "tu". */
    @Test
    @Config(qualifiers = "fr-rFR")
    fun `AN8 French announcements`() {
        launchWithVoice(TtsTestSupport.frenchLocal)
        playEveryAnnouncement()

        assertEquals("Ann, c'est toi qui commences.", audio.allSpoken.first())
        assertEquals(
            listOf(
                "Ann, c'est ton tour. Il te reste 121.",
                "Pas de score",
                "Ann, à toi de jouer. Il te reste 121.",
                "Ann, c'est ton tour. Il te reste 121.",
                "94",
                "Ann, à toi de jouer. Il te reste 27.",
                "Victoire de Ann",
                "On commence avec Ann.",
                "131",
                "Ann, c'est ton tour. Il te reste 170.",
                "Victoire de Ann",
            ),
            audio.timeline.speech,
        )
    }

    /** AN4, AN22, E1: the first player is announced with a game start phrase, once the engine is ready. */
    @Test
    fun `AN4 the first player is announced at game start`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        assertEquals(emptyList<String>(), spoken)

        initLastEngine()
        playEverything()

        assertEquals(listOf("Ann, you're throwing first."), spoken)
    }

    /** AN10, E2: dart sounds, then the 180 sound (no voice), then the next turn's voice after the 180 sound. */
    @Test
    fun `AN10 order of sounds and voices`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        playEverything()
        audio.timeline.heard.clear()

        triple(20); triple(20); triple(20)
        nextPlayer()
        playEverything()

        assertEquals(listOf("DART_HIT", "DART_HIT", "DART_HIT", "ONE_EIGHTY", "Bob, your turn."), heardAsText())
        val oneEighty = audio.timeline.heard[3].at
        val voice = audio.timeline.heard[4].at
        assertTrue("the voice waits for the 180 sound", voice - oneEighty >= 4_000)
    }

    /** AN23: an undo during an announcement stops everything, then plays the cancel sound. */
    @Test
    fun `AN23 undo stops everything then plays the cancel sound`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        playEverything()

        triple(20); triple(20); triple(20)
        undo()
        playEverything()

        assertEquals(threeHits + SoundEffect.DART_CANCEL, sounds)
        assertEquals(listOf("Ann, you're throwing first."), spoken)
    }

    /** AN23, AN14: with sounds off, an undo plays nothing. */
    @Test
    fun `AN23 undo with sounds off plays nothing`() {
        process.app.audioSettings.setSoundsEnabled(false)
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()

        triple(20); triple(20); triple(20)
        undo()
        playEverything()

        assertEquals(emptyList<SoundEffect>(), sounds)
    }

    /** AN12, AN16 (D5): only a network voice: nothing is spoken, and the first-game message shows. */
    @Test
    fun `AN12 AN16 only a network voice - silence and the message`() {
        launchWithVoice(TtsTestSupport.englishNetwork)
        startGame("Ann", "Bob")
        initLastEngine()
        playEverything()
        key(GameTags.NO_VOICE_DIALOG).assertExists()
        composeRule.onNodeWithText(string(R.string.no_voice_ok)).performClick()
        triple(20); triple(20); triple(20)
        playEverything()

        assertEquals(emptyList<String>(), spoken)
        assertNull(lastEngine().currentVoice)
        assertEquals(threeHits + SoundEffect.ONE_EIGHTY, sounds)
    }

    /** AN12, AN16: a voice marked not installed is the same as no voice. */
    @Test
    fun `AN12 AN16 only a voice that is not installed - silence and the message`() {
        launchWithVoice(TtsTestSupport.englishNotInstalled)
        startGame("Ann", "Bob")
        initLastEngine()
        playEverything()

        assertEquals(emptyList<String>(), spoken)
        key(GameTags.NO_VOICE_DIALOG).assertExists()
    }

    /** AN16: the message shows once on this device: not at the next game, nor after the app is stopped. */
    @Test
    fun `AN16 the message shows once`() {
        launchWithVoice(TtsTestSupport.englishNetwork)
        startGame("Ann", "Bob")
        initLastEngine()
        playEverything()
        composeRule.onNodeWithText(string(R.string.no_voice_ok)).performClick()
        composeRule.waitForIdle()
        key(GameTags.NO_VOICE_DIALOG).assertDoesNotExist()

        abandonToHome()
        startGame("Ann", "Bob")
        initLastEngine()
        playEverything()
        key(GameTags.NO_VOICE_DIALOG).assertDoesNotExist()

        abandonToHome()
        process.launchFresh()
        startGame("Ann", "Bob")
        initLastEngine()
        playEverything()
        key(GameTags.NO_VOICE_DIALOG).assertDoesNotExist()
        assertTrue(process.app.audioSettings.settings.value.noVoiceNoticeShown)
    }

    /** AN16: the message stays open across a rotation and is not shown again after it. */
    @Test
    fun `AN16 the message survives a rotation`() {
        launchWithVoice(TtsTestSupport.englishNetwork)
        startGame("Ann", "Bob")
        initLastEngine()
        playEverything()

        process.rotate()
        initLastEngine()
        playEverything()

        key(GameTags.NO_VOICE_DIALOG).assertExists()
    }

    /** AN14: voice off: no engine is started, nothing is spoken; sounds play. */
    @Test
    fun `AN14 voice off speaks nothing`() {
        process.app.audioSettings.setVoiceEnabled(false)
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        triple(20); triple(20); triple(20)
        playEverything()

        assertNull(ShadowTextToSpeech.getLastTextToSpeechInstance())
        assertEquals(threeHits + SoundEffect.ONE_EIGHTY, sounds)
        key(GameTags.NO_VOICE_DIALOG).assertDoesNotExist()
    }

    /** AN14: sounds off: no sound; the voice speaks. */
    @Test
    fun `AN14 sounds off plays nothing`() {
        process.app.audioSettings.setSoundsEnabled(false)
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        single(20, 20, 20)
        playEverything()

        assertEquals(emptyList<SoundEffect>(), sounds)
        assertEquals(listOf("Ann, you're throwing first.", "60"), spoken)
    }

    /** AN7: a rotation replays nothing. */
    @Test
    fun `AN7 nothing is replayed after a rotation`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        single(20, 20, 20)
        playEverything()
        val heardBefore = audio.timeline.heard.toList()
        assertEquals(listOf("Ann, you're throwing first.", "60"), audio.allSpoken)

        process.rotate()
        initLastEngine()
        playEverything()

        assertEquals(heardBefore, audio.timeline.heard)
        assertEquals(listOf("Ann, you're throwing first.", "60"), audio.allSpoken)
    }

    /** AN7: resuming a waiting turn (normal, bust or checkout), then returning after a kill: no replay. */
    @Test
    fun `AN7 nothing is replayed after a resume`() {
        ShadowTextToSpeech.addVoice(TtsTestSupport.englishLocal)
        listOf(SaveFixtures.WAITING_NORMAL, SaveFixtures.WAITING_BUST, SaveFixtures.WAITING_CHECKOUT).forEach { saved ->
            process.finish()
            process.writeGame(saved)
            process.launchFresh()
            composeRule.onNodeWithText(string(R.string.home_resume_game)).performScrollTo().performClick()
            composeRule.waitForIdle()
            initLastEngine()
            playEverything()

            process.killInBackgroundAndReturn()
            composeRule.waitForIdle()
            initLastEngine()
            playEverything()
        }

        assertEquals(emptyList<Heard>(), audio.timeline.heard)
        assertEquals(6, audio.speakers.size) // one engine per return to the game screen
        assertEquals(emptyList<String>(), audio.allSpoken)
        key(GameTags.NO_VOICE_DIALOG).assertDoesNotExist()
    }

    /** AN20: refused focus skips the announcement; sounds still play. */
    @Test
    fun `AN20 refused focus skips the voice, not the sounds`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        playEverything()
        val audioManager = process.app.getSystemService(AudioManager::class.java)
        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        single(20, 20, 20)
        playEverything()

        assertEquals(listOf("Ann, you're throwing first."), spoken)
        assertEquals(threeHits, sounds)
    }

    /** AN21: when the game screen is no longer visible, nothing pending plays, and the engine is shut down. */
    @Test
    fun `AN21 a hidden game screen plays nothing`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        playEverything()
        val engine = lastEngine()

        triple(20); triple(20); triple(20)
        composeRule.waitForIdle()
        process.stopActivity()
        playEverything()

        assertEquals(threeHits, sounds)
        assertEquals(listOf("Ann, you're throwing first."), engine.spokenTextList)
        assertTrue(engine.isShutdown)
        assertTrue(audio.soundPlayers.single().isReleased)

        // Back in the app: nothing is replayed, and only new cues play (AN7).
        process.restartActivity()
        composeRule.waitForIdle()
        initLastEngine()
        playEverything()
        assertEquals(threeHits, sounds)
        assertEquals(emptyList<String>(), lastEngine().spokenTextList)

        nextPlayer()
        single(1)
        playEverything()
        assertEquals(listOf("Bob, your turn."), lastEngine().spokenTextList)
        assertEquals(threeHits + SoundEffect.DART_HIT, sounds)
    }

    /** AN18: a voice failing mid-game goes silent, without a message; sounds go on. */
    @Test
    fun `AN18 a voice failing mid-game goes silent without a message`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        playEverything()

        single(1, 1, 1)
        nextPlayer()
        composeRule.waitForIdle()
        // The engine fails on Bob's turn phrase before it reports the end of that text.
        runUntilSpoken("Bob, your turn.")
        lastEngine().utteranceProgressListener.onError("announcement-2", TextToSpeech.ERROR_SYNTHESIS)
        playEverything()
        single(1, 1, 1)
        nextPlayer()
        playEverything()

        assertEquals(listOf("Ann, you're throwing first.", "3", "Bob, your turn."), spoken)
        key(GameTags.NO_VOICE_DIALOG).assertDoesNotExist()
        assertEquals(6, sounds.count { it == SoundEffect.DART_HIT })
    }

    /** AN19: the volume buttons set the media volume on the game and end screens, not on the other screens. */
    @Test
    fun `AN19 volume buttons follow the media volume on the game screen`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        assertEquals(AudioManager.USE_DEFAULT_STREAM_TYPE, process.activity.volumeControlStream)

        startGame("Ann", "Bob")
        assertEquals(AudioManager.STREAM_MUSIC, process.activity.volumeControlStream)

        process.rotate()
        composeRule.waitForIdle()
        assertEquals(AudioManager.STREAM_MUSIC, process.activity.volumeControlStream)

        abandonToHome()
        assertEquals(AudioManager.USE_DEFAULT_STREAM_TYPE, process.activity.volumeControlStream)
    }
}
