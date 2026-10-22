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
import io.github.darkcosmos.kosmodarts.ui.end.EndTags
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.revealIfScrolled
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
 * The owner evolutions E1 to E5 through the screens (docs/rules/announcements.md v2): phrase variants, sounds that
 * replace the bust and 180 voice, the win on the winning dart, and dart calls.
 */
internal class OwnerEvolutionsScreenTest : AnnouncementsScreenTestBase() {
    /** AN26, E1: next-turn phrases change at every turn, never twice the same in a row. */
    @Test
    fun `AN26 next turn phrases never repeat twice in a row`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        repeat(4) {
            single(1, 1, 1)
            nextPlayer()
            playEverything()
        }

        val turns = spoken.drop(1).filter { it != "3" } // the turn scores are "3"
        assertEquals(listOf("Bob, your turn.", "Ann, you're up.", "Bob, your turn.", "Ann, you're up."), turns)
        val phrases = turns.map { it.replace("Ann", "{name}").replace("Bob", "{name}") }
        phrases.zipWithNext().forEach { (first, second) -> assertNotEquals(first, second) }
    }

    /** E2: bust and 180 give their sound and no voice; with sounds off, silence. */
    @Test
    fun `E2 bust and 180 have no voice, and are silent with sounds off`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann")
        initLastEngine()
        triple(20); triple(20); triple(20) // 180, 121 left
        playEverything()
        nextPlayer()
        triple(20); triple(20) // bust
        playEverything()
        val withSounds =
            threeHits + SoundEffect.ONE_EIGHTY + SoundEffect.DART_HIT + SoundEffect.DART_HIT + SoundEffect.BUST
        assertEquals(withSounds, sounds)
        assertEquals(listOf("Ann, your turn. You require 121."), audio.timeline.speech)

        process.app.audioSettings.setSoundsEnabled(false)
        nextPlayer()
        triple(20); triple(20) // the bust again: nothing is heard but the next turn phrase
        playEverything()
        assertEquals(withSounds, sounds)
        assertEquals(
            listOf("Ann, your turn. You require 121.", "Ann, you're up. You require 121."),
            audio.timeline.speech,
        )
    }

    /** AN5, E3, A.2: the victory sound and the win voice start together; "Finish game" plays nothing. */
    @Test
    fun `AN5 the win plays on the winning dart and Finish game plays nothing`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        playAnnTo40()
        audio.timeline.heard.clear()

        double(20)
        composeRule.onNodeWithTag(GameTags.CHECKOUT_BANNER).assertExists()
        playEverything()
        assertEquals(listOf("DART_HIT", "VICTORY", "Game shot! Ann wins"), heardAsText())
        // A.2: the victory sound and the win voice start together.
        assertEquals(audio.timeline.heard[1].at, audio.timeline.heard[2].at)

        finishGame()
        playEverything()
        assertEquals(listOf("DART_HIT", "VICTORY", "Game shot! Ann wins"), heardAsText())
    }

    /** E3, T14: "Finish game" while the win voice is spoken: the voice is not cut, no second win, the rematch after. */
    @Test
    fun `E3 Finish game during the win voice cuts nothing and the rematch is announced after it`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        playAnnTo40()

        double(20)
        composeRule.waitForIdle()
        runUntilSpoken("Game shot! Ann wins")
        val engine = lastEngine()
        finishGame()
        composeRule.waitForIdle()
        assertTrue("the win voice is not stopped", !engine.isStopped)
        playEverything()

        composeRule.onNodeWithTag(EndTags.REMATCH).revealIfScrolled().performClick()
        playEverything()

        assertEquals(1, audio.timeline.speech.count { it == "Game shot! Ann wins" })
        assertEquals(1, sounds.count { it == SoundEffect.VICTORY })
        assertEquals("Bob to throw first.", spoken.last())
        assertEquals(AudioManager.STREAM_MUSIC, process.activity.volumeControlStream)
        assertEquals(1, audio.speakers.size) // the same session went on from the game to the end screen
    }

    /** E3, AN23: refusing the checkout dialog stops the win and plays the cancel sound. */
    @Test
    fun `E3 refusing the checkout dialog stops the win and plays the cancel sound`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        playAnnTo40()
        audio.timeline.heard.clear()

        double(20)
        composeRule.waitForIdle()
        undoInCheckoutDialog()
        playEverything()

        assertEquals(listOf("DART_HIT", "DART_CANCEL"), heardAsText())
        key(GameTags.CHECKOUT_BANNER).assertDoesNotExist()
    }

    /** AN6, E3: the winning dart undone then entered again gives the win again. */
    @Test
    fun `AN6 the winning dart entered again gives the win again`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        playAnnTo40()

        double(20)
        playEverything()
        undoInCheckoutDialog()
        double(20)
        playEverything()

        assertEquals(2, audio.timeline.speech.count { it == "Game shot! Ann wins" })
        assertEquals(2, sounds.count { it == SoundEffect.VICTORY })
    }

    /** E2, E3: with sounds off, the win is still spoken. */
    @Test
    fun `E2 with sounds off the win is still spoken`() {
        process.app.audioSettings.setSoundsEnabled(false)
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        playAnnTo40()

        double(20)
        playEverything()

        assertEquals("Game shot! Ann wins", spoken.last())
        assertEquals(emptyList<SoundEffect>(), sounds)
    }

    /** AN25: each dart is called after its impact sound, a miss is not; the last dart's call comes before the score. */
    @Test
    fun `AN25 each dart is called after its impact`() {
        process.app.audioSettings.setCallEachDart(true)
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        playEverything()
        audio.timeline.heard.clear()

        single(18)
        playEverything()
        double(20)
        playEverything()
        triple(20)
        playEverything()
        nextPlayer()
        playEverything()
        tap(KeypadTags.BULL)
        playEverything()
        miss()
        playEverything()

        assertEquals(
            listOf(
                "DART_HIT", "18",
                "DART_HIT", "Double 20",
                "DART_HIT", "Triple 20", "118",
                "Bob, your turn.",
                "DART_HIT", "Bull",
                "DART_MISS",
            ),
            heardAsText(),
        )
    }

    /** AN25: fast entry: a new dart cancels the call of the previous one; nothing is said late. */
    @Test
    fun `AN25 a new dart cancels the call of the previous one`() {
        process.app.audioSettings.setCallEachDart(true)
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        playEverything()
        audio.timeline.heard.clear()

        single(18)
        double(20)
        triple(20)
        playEverything()

        assertEquals(listOf("DART_HIT", "DART_HIT", "DART_HIT", "Triple 20", "118"), heardAsText())
    }

    /** AN25, E3: on a checkout dart: impact, call, win sound, win voice. */
    @Test
    fun `AN25 the checkout dart is called before the win`() {
        process.app.audioSettings.setCallEachDart(true)
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        playAnnTo40()
        audio.timeline.heard.clear()

        double(20)
        playEverything()

        assertEquals(listOf("DART_HIT", "Double 20", "VICTORY", "Game shot! Ann wins"), heardAsText())
    }

    /** AN25, AN14: with "Call each dart" off (the default) or the voice off, no dart is called. */
    @Test
    fun `AN25 no call by default`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        playEverything()
        audio.timeline.heard.clear()

        single(18)
        playEverything()

        assertEquals(listOf("DART_HIT"), heardAsText())
    }
    /** A.4, S10: the engine starts during setup, where nothing is spoken; the first phrase is immediate at Start. */
    @Test
    fun `A4 the first phrase is immediate when the engine was ready during setup`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        openSetup("Ann", "Bob")
        initLastEngine()
        playEverything()
        assertEquals(emptyList<String>(), spoken)

        tapStart()

        assertEquals(listOf("Ann, you're throwing first."), spoken)
        assertEquals(1, audio.speakers.size)
    }
    /** A.4, T23: with the voice off, no speech engine is started on the setup screen either. */
    @Test
    fun `A4 voice off starts no engine on the setup screen`() {
        process.app.audioSettings.setVoiceEnabled(false)
        launchWithVoice(TtsTestSupport.englishLocal)
        openSetup("Ann", "Bob")

        assertNull(ShadowTextToSpeech.getLastTextToSpeechInstance())
        assertEquals(1, audio.soundPlayers.size)
    }

    /** A.4, T23, AN21: leaving the setup screen for home, or the app going to the background, releases the session. */
    @Test
    fun `A4 the setup session is released when leaving setup or the app`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        openSetup("Ann", "Bob")
        val first = lastEngine()
        process.pressBack()
        composeRule.waitForIdle()
        assertTrue(first.isShutdown)
        assertTrue(audio.soundPlayers.single().isReleased)

        openSetup("Ann", "Bob")
        val second = lastEngine()
        assertTrue(!second.isShutdown)
        process.stopActivity()
        assertTrue(second.isShutdown)
        process.restartActivity()
        composeRule.waitForIdle()
        assertEquals(3, audio.speakers.size)
        assertTrue(!lastEngine().isShutdown)
    }

    /** A.4, S10, T23, AN7: nothing is played or spoken on the setup screen, even after a rotation. */
    @Test
    fun `A4 nothing is played on the setup screen, even after a rotation`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        openSetup("Ann", "Bob")
        val cues = mutableListOf<io.github.darkcosmos.kosmodarts.engine.x01.X01Cue>()
        process.viewModel.addCueSink { cues.addAll(it) }
        initLastEngine()
        process.rotate()
        composeRule.waitForIdle()
        initLastEngine()
        playEverything()

        assertEquals(emptyList<io.github.darkcosmos.kosmodarts.engine.x01.X01Cue>(), cues)
        assertEquals(emptyList<String>(), audio.allSpoken)
        assertEquals(emptyList<Heard>(), audio.timeline.heard)
        assertEquals(AudioManager.USE_DEFAULT_STREAM_TYPE, process.activity.volumeControlStream)
    }

    /** A.4, AN16: a check that ended during setup still opens the message once, on the first game only. */
    @Test
    fun `A4 the no voice message opens at Start when the check ended during setup, and not on a rematch`() {
        launchWithVoice(TtsTestSupport.englishNetwork)
        openSetup("Ann")
        initLastEngine()
        playEverything()
        key(GameTags.NO_VOICE_DIALOG).assertDoesNotExist()

        tapStart()
        playEverything()
        key(GameTags.NO_VOICE_DIALOG).assertExists()
        composeRule.onNodeWithText(string(R.string.no_voice_ok)).performClick()
        composeRule.waitForIdle()

        playAnnToWin()
        finishGame()
        composeRule.onNodeWithTag(EndTags.REMATCH).revealIfScrolled().performClick()
        playEverything()
        key(GameTags.NO_VOICE_DIALOG).assertDoesNotExist()
    }

    /** One player from 301: 180 (121 left), then T20, T11, D14. */
    private fun playAnnToWin() {
        triple(20); triple(20); triple(20)
        nextPlayer()
        triple(20); triple(11); double(14)
        playEverything()
    }
}
