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
 * Launches the app with real text-to-speech (Robolectric's) and recording sound players, and drives games through
 * the screens. Shared by the announcement screen tests.
 */
@RunWith(AndroidJUnit4::class)
internal abstract class AnnouncementsScreenTestBase {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    protected val process = AppProcess()
    protected val audio = TestAudioFactory()

    @Before
    fun setUp() = process.app.replaceAudioFactory(audio)

    @After
    fun tearDown() {
        process.close()
        ShadowTextToSpeech.reset()
    }

    protected fun string(id: Int, vararg args: Any): String = process.string(id, *args)

    protected fun key(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    protected fun tap(vararg tags: String) = tags.forEach { key(it).performClick() }

    protected fun triple(number: Int) = tap(KeypadTags.TRIPLE, KeypadTags.number(number))

    protected fun double(number: Int) = tap(KeypadTags.DOUBLE, KeypadTags.number(number))

    protected fun single(vararg numbers: Int) = numbers.forEach { tap(KeypadTags.number(it)) }

    protected fun miss(times: Int = 1) = repeat(times) { tap(KeypadTags.MISS) }

    protected fun nextPlayer() = tap(KeypadTags.NEXT_PLAYER)

    protected fun undo() = tap(KeypadTags.UNDO)

    protected fun finishGame() = composeRule.onNodeWithText(string(R.string.checkout_finish)).performClick()

    /** Lets every pending sound and voice play. */
    protected fun playEverything() {
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        composeRule.waitForIdle()
    }

    /**
     * Runs the main looper one task at a time until the engine has received [text], so that a test can act before
     * the engine reports its end. Independent of how many tasks lead there.
     */
    protected fun runUntilSpoken(text: String) {
        val looper = shadowOf(Looper.getMainLooper())
        repeat(MAX_TASKS) {
            if (text in spoken) return
            looper.runOneTask()
        }
        error("\"$text\" was never sent to the engine")
    }

    protected val spoken: List<String>
        get() = ShadowTextToSpeech.getLastTextToSpeechInstance()?.let { shadowOf(it).spokenTextList }.orEmpty()

    protected val sounds: List<SoundEffect>
        get() = audio.timeline.sounds

    protected val threeHits = List(3) { SoundEffect.DART_HIT }

    /** From home: one player (or more), 301 double out, then Start. */
    protected fun startGame(vararg names: String = arrayOf("Ann")) {
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

    protected fun launchWithVoice(vararg voices: android.speech.tts.Voice) {
        voices.forEach(ShadowTextToSpeech::addVoice)
        process.launchFresh()
    }

    /** Back on the game screen, "Abandon": the home screen. */
    protected fun abandonToHome() {
        process.pressBack()
        composeRule.onNodeWithText(string(R.string.game_abandon_confirm)).performClick()
        composeRule.waitForIdle()
    }

    /** The heard timeline as text: sound names and spoken texts, in order. */
    protected fun heardAsText(): List<String> = audio.timeline.heard.map {
        when (it) {
            is Heard.Sound -> it.effect.name
            is Heard.Speech -> it.text
        }
    }

    /**
     * One player, 301: 180, no score, a bust, 94, then a checkout from 27; then a rematch: 131, then a big fish.
     * Covers every line of the AN8 table but the Settings and dart call ones. The draw of variants is fixed: always
     * the first variant that is allowed (AN26).
     */
    protected fun playEveryAnnouncement() {
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
        single(7); double(10) // checkout from 27: the win plays with the dialog (E3)
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

    /** 301, Ann against Bob, every turn confirmed: Ann scores 261 and is at the start of a turn with 40 left. */
    protected fun playAnnTo40() {
        triple(20); triple(20); triple(20) // 121
        nextPlayer()
        single(1, 1, 1)
        nextPlayer()
        triple(20); single(20, 1) // 40
        nextPlayer()
        single(1, 1, 1)
        nextPlayer()
        playEverything()
    }

    /** The Undo button of the checkout dialog, not the Undo key of the keypad. */
    protected fun undoInCheckoutDialog() {
        composeRule.onNode(
            hasText(string(R.string.key_undo)).and(hasAnyAncestor(hasTestTag(GameTags.CHECKOUT_DIALOG))),
        ).performClick()
        composeRule.waitForIdle()
    }

    protected val winSounds = setOf(SoundEffect.VICTORY)

    private companion object {
        /** More than any announcement ever schedules: a guard against an endless loop. */
        const val MAX_TASKS = 100
    }
}
