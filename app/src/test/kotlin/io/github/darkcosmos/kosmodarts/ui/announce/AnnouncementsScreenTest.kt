package io.github.darkcosmos.kosmodarts.ui.announce

import android.media.AudioManager
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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
@RunWith(AndroidJUnit4::class)
class AnnouncementsScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val process = AppProcess()
    private val audio = TestAudioFactory()

    @Before
    fun setUp() = process.app.replaceAudioFactory(audio)

    @After
    fun tearDown() {
        process.close()
        ShadowTextToSpeech.reset()
    }

    private fun string(id: Int, vararg args: Any): String = process.string(id, *args)

    private fun key(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun tap(vararg tags: String) = tags.forEach { key(it).performClick() }

    private fun triple(number: Int) = tap(KeypadTags.TRIPLE, KeypadTags.number(number))

    private fun double(number: Int) = tap(KeypadTags.DOUBLE, KeypadTags.number(number))

    private fun single(vararg numbers: Int) = numbers.forEach { tap(KeypadTags.number(it)) }

    private fun miss(times: Int = 1) = repeat(times) { tap(KeypadTags.MISS) }

    private fun nextPlayer() = tap(KeypadTags.NEXT_PLAYER)

    private fun undo() = tap(KeypadTags.UNDO)

    private fun finishGame() = composeRule.onNodeWithText(string(R.string.checkout_finish)).performClick()

    /** Lets every pending sound and voice play. */
    private fun playEverything() {
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        composeRule.waitForIdle()
    }

    private val spoken: List<String>
        get() = ShadowTextToSpeech.getLastTextToSpeechInstance()?.let { shadowOf(it).spokenTextList }.orEmpty()

    private val sounds: List<SoundEffect>
        get() = audio.timeline.sounds

    /** From home: one player (or more), 301 double out, then Start. */
    private fun startGame(vararg names: String = arrayOf("Ann")) {
        composeRule.onNodeWithText(string(R.string.home_new_game)).performScrollTo().performClick()
        composeRule.onNodeWithText("301").performClick()
        if (names.size == 1) {
            composeRule.onNodeWithContentDescription(string(R.string.setup_remove_player)).performClick()
        }
        names.forEachIndexed { index, name ->
            composeRule.onNodeWithTag(SetupTags.nameField(index)).performScrollTo().performTextReplacement(name)
        }
        composeRule.onNodeWithText(string(R.string.setup_start)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun launchWithVoice(vararg voices: android.speech.tts.Voice) {
        voices.forEach(ShadowTextToSpeech::addVoice)
        process.launchFresh()
    }

    /** Back on the game screen, "Abandon": the home screen. */
    private fun abandonToHome() {
        process.pressBack()
        composeRule.onNodeWithText(string(R.string.game_abandon_confirm)).performClick()
        composeRule.waitForIdle()
    }

    /**
     * One player, 301: 180, no score, a bust, 94, then a checkout from 27; then a second game: 131, then a big fish.
     * Covers every line of the AN8 table but the Settings one.
     */
    private fun playEveryAnnouncement() {
        startGame("Ann")
        initLastEngine()
        triple(20); triple(20); triple(20) // 180, 121 left
        playEverything()
        nextPlayer()
        miss(3) // no score
        playEverything()
        nextPlayer()
        triple(20); triple(20) // 1 left: bust
        playEverything()
        nextPlayer()
        triple(20); triple(11); single(1) // 94, 27 left
        playEverything()
        nextPlayer()
        single(7); double(10) // checkout from 27
        playEverything()
        finishGame()
        playEverything()

        composeRule.onNodeWithText(string(R.string.end_rematch)).performScrollTo().performClick()
        composeRule.waitForIdle()
        // The end screen kept the voice: the rematch's first player is announced at once.
        triple(20); triple(20); single(11) // 131, 170 left
        playEverything()
        nextPlayer()
        triple(20); triple(20); tap(KeypadTags.BULL) // big fish
        playEverything()
        finishGame()
        playEverything()
    }

    /** AN8: every English announcement of a game, in order. */
    @Test
    @Config(qualifiers = "en-rUS")
    fun `AN8 English announcements`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        playEveryAnnouncement()

        assertEquals(
            listOf(
                "One hundred and eighty!",
                "Ann, you require 121",
                "No score",
                "Ann, you require 121",
                "Bust",
                "Ann, you require 121",
                "94",
                "Ann, you require 27",
                "Game shot! Ann wins",
                "Ann",
                "131",
                "Ann, you require 170",
                "Big Fish! Ann wins",
            ),
            audio.timeline.speech,
        )
    }

    /** AN8: the same game in French. */
    @Test
    @Config(qualifiers = "fr-rFR")
    fun `AN8 French announcements`() {
        launchWithVoice(TtsTestSupport.frenchLocal)
        playEveryAnnouncement()

        assertEquals(
            listOf(
                "Cent quatre-vingts\u00A0!",
                "Ann, il te reste 121",
                "Pas de score",
                "Ann, il te reste 121",
                "Bust",
                "Ann, il te reste 121",
                "94",
                "Ann, il te reste 27",
                "Victoire de Ann",
                "Ann",
                "131",
                "Ann, il te reste 170",
                "Big Fish\u00A0! Victoire de Ann",
            ),
            audio.timeline.speech,
        )
    }

    /** AN4, AN22: the first player's name is spoken at game start, once the engine is ready. */
    @Test
    fun `AN4 the first player's name is spoken at game start`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        assertEquals(emptyList<String>(), spoken)

        initLastEngine()
        playEverything()

        assertEquals(listOf("Ann"), spoken)
    }

    /** AN10: dart sounds, then the event sound, then the end-of-turn voice, then the next turn's voice. */
    @Test
    fun `AN10 order of sounds and voices`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        playEverything()
        audio.timeline.heard.clear()

        triple(20); triple(20); triple(20)
        playEverything()
        nextPlayer()
        playEverything()

        val heard = audio.timeline.heard.map {
            when (it) {
                is Heard.Sound -> it.effect.name
                is Heard.Speech -> it.text
            }
        }
        assertEquals(listOf("DART_HIT", "DART_HIT", "DART_HIT", "ONE_EIGHTY", "One hundred and eighty!", "Bob"), heard)
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

        assertEquals(listOf(SoundEffect.DART_HIT, SoundEffect.DART_HIT, SoundEffect.DART_HIT, SoundEffect.DART_CANCEL), sounds)
        assertEquals(listOf("Ann"), spoken)
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
        assertEquals(listOf(SoundEffect.DART_HIT, SoundEffect.DART_HIT, SoundEffect.DART_HIT, SoundEffect.ONE_EIGHTY), sounds)
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
        assertEquals(listOf(SoundEffect.DART_HIT, SoundEffect.DART_HIT, SoundEffect.DART_HIT, SoundEffect.ONE_EIGHTY), sounds)
        key(GameTags.NO_VOICE_DIALOG).assertDoesNotExist()
    }

    /** AN14: sounds off: no sound; the voice speaks. */
    @Test
    fun `AN14 sounds off plays nothing`() {
        process.app.audioSettings.setSoundsEnabled(false)
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        triple(20); triple(20); triple(20)
        playEverything()

        assertEquals(emptyList<SoundEffect>(), sounds)
        assertEquals(listOf("Ann", "One hundred and eighty!"), spoken)
    }

    /** AN7: a rotation replays nothing. */
    @Test
    fun `AN7 nothing is replayed after a rotation`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        triple(20); triple(20); triple(20)
        playEverything()
        val heardBefore = audio.timeline.heard.toList()
        val spokenBefore = spoken.toList()

        process.rotate()
        initLastEngine()
        playEverything()

        assertEquals(heardBefore, audio.timeline.heard)
        assertEquals(emptyList<String>(), spoken - spokenBefore.toSet())
        assertEquals(listOf("Ann", "One hundred and eighty!"), spokenBefore)
    }

    /** AN7: resuming a waiting turn, after the app was stopped or killed, replays nothing. */
    @Test
    fun `AN7 nothing is replayed after a resume`() {
        ShadowTextToSpeech.addVoice(TtsTestSupport.englishLocal)
        process.writeGame(SaveFixtures.WAITING_BUST)
        process.launchFresh()
        composeRule.onNodeWithText(string(R.string.home_resume_game)).performScrollTo().performClick()
        composeRule.waitForIdle()
        initLastEngine()
        playEverything()

        process.killInBackgroundAndReturn()
        composeRule.waitForIdle()
        initLastEngine()
        playEverything()

        assertEquals(emptyList<Heard>(), audio.timeline.heard)
        assertEquals(emptyList<String>(), spoken)
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

        triple(20); triple(20); triple(20)
        playEverything()

        assertEquals(listOf("Ann"), spoken)
        assertEquals(SoundEffect.ONE_EIGHTY, sounds.last())
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

        assertEquals(listOf(SoundEffect.DART_HIT, SoundEffect.DART_HIT, SoundEffect.DART_HIT), sounds)
        assertEquals(listOf("Ann"), engine.spokenTextList)
        assertTrue(engine.isShutdown)
        assertTrue(audio.soundPlayers.single().isReleased)
        process.restartActivity()
    }

    /** AN18: a voice that fails during the game goes silent, without a message; sounds go on. */
    @Test
    fun `AN18 a voice failing mid-game goes silent without a message`() {
        launchWithVoice(TtsTestSupport.englishLocal)
        startGame("Ann", "Bob")
        initLastEngine()
        playEverything()

        triple(20); triple(20); triple(20)
        composeRule.waitForIdle()
        val looper = shadowOf(Looper.getMainLooper())
        // Runs the 180 sound, then the step that sends the 180 text to the engine, then makes the engine fail on it
        // before it reports the end of that text.
        looper.runOneTask()
        looper.runOneTask()
        assertEquals(listOf("Ann", "One hundred and eighty!"), spoken)
        lastEngine().utteranceProgressListener.onError("announcement-1", TextToSpeech.ERROR_SYNTHESIS)
        playEverything()
        nextPlayer()
        triple(19); single(1); single(1)
        playEverything()

        assertEquals(listOf("Ann", "One hundred and eighty!"), spoken)
        key(GameTags.NO_VOICE_DIALOG).assertDoesNotExist()
        assertEquals(SoundEffect.DART_HIT, sounds.last())
        assertEquals(7, sounds.size) // 3 hits, the 180 sound, then 3 hits
    }

    /** AN19: the volume buttons set the media volume on the game screen only. */
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
