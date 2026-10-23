package io.github.darkcosmos.kosmodarts.ui.settings

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
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
import io.github.darkcosmos.kosmodarts.audio.TestAudioFactory
import io.github.darkcosmos.kosmodarts.audio.TtsTestSupport
import io.github.darkcosmos.kosmodarts.audio.TtsTestSupport.initLastEngine
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.settings.AudioSettingsRepository
import io.github.darkcosmos.kosmodarts.settings.SharedPreferencesAudioSettingsStore
import io.github.darkcosmos.kosmodarts.ui.home.HomeTags
import io.github.darkcosmos.kosmodarts.ui.resume.AppProcess
import io.github.darkcosmos.kosmodarts.ui.setup.SetupTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech

/** The Settings screen, through the app's activity (docs/rules/announcements.md, AN13 to AN17). */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

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

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun openSettings() {
        node(HomeTags.SETTINGS).performClick()
        composeRule.waitForIdle()
    }

    private fun back() {
        process.pressBack()
        composeRule.waitForIdle()
    }

    private val spoken: List<String>
        get() = ShadowTextToSpeech.getLastTextToSpeechInstance()?.let { shadowOf(it).spokenTextList }.orEmpty()

    /** A new process: the settings are read again from the device. */
    private fun newProcess() {
        process.finish()
        val context: Context = process.app
        process.app.replaceAudioSettings(
            AudioSettingsRepository(
                SharedPreferencesAudioSettingsStore(
                    context.getSharedPreferences(SharedPreferencesAudioSettingsStore.FILE_NAME, Context.MODE_PRIVATE),
                ),
            ),
        )
        process.launchFresh()
    }

    private fun registerInstallScreen(enginePackage: String) {
        audio.enginePackage = enginePackage
        TtsTestSupport.registerInstallScreen(process.app, enginePackage)
    }

    @Test
    fun `AN13 the gear on home opens Settings, and back returns home`() {
        process.launchFresh()
        node(HomeTags.SETTINGS).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.home_settings)).assertIsDisplayed()

        openSettings()
        composeRule.onNodeWithText(string(R.string.settings_title)).assertIsDisplayed()

        back()
        node(HomeTags.SETTINGS).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.home_new_game)).assertIsDisplayed()
    }

    @Test
    fun `AN13 the back arrow returns home with the saved game summary`() {
        process.writeGame(SaveFixtures.MID_TURN)
        process.launchFresh()
        openSettings()

        node(SettingsTags.BACK).performClick()
        composeRule.waitForIdle()

        node(HomeTags.SUMMARY).performScrollTo().assertIsDisplayed()
        assertTrue(process.gameFile.exists())
    }

    @Test
    fun `AN13 no gear on the setup and game screens, and the game screen has no sound control`() {
        process.launchFresh()
        composeRule.onNodeWithTag(HomeTags.MODE_X01).performClick()
        node(HomeTags.SETTINGS).assertDoesNotExist()

        composeRule.onNodeWithTag(SetupTags.nameField(0)).performTextReplacement("Ann")
        composeRule.onNodeWithTag(SetupTags.nameField(1)).performTextReplacement("Bob")
        composeRule.onNodeWithTag(SetupTags.START).performClick()
        composeRule.waitForIdle()

        node(HomeTags.SETTINGS).assertDoesNotExist()
        node(SettingsTags.VOICE_SWITCH).assertDoesNotExist()
        node(SettingsTags.SOUNDS_SWITCH).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.settings_voice)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.settings_sounds)).assertDoesNotExist()
    }

    @Test
    fun `AN14 both switches are on by default`() {
        process.launchFresh()
        openSettings()

        node(SettingsTags.VOICE_SWITCH).assertIsOn()
        node(SettingsTags.SOUNDS_SWITCH).assertIsOn()
    }

    @Test
    fun `AN14 AN15 the switches are independent and kept after a rotation and a new process`() {
        ShadowTextToSpeech.addVoice(TtsTestSupport.englishLocal)
        process.launchFresh()
        openSettings()
        node(SettingsTags.SOUNDS_SWITCH).performClick()
        node(SettingsTags.VOICE_SWITCH).assertIsOn()
        node(SettingsTags.SOUNDS_SWITCH).assertIsOff()

        process.rotate()
        composeRule.waitForIdle()
        node(SettingsTags.SOUNDS_SWITCH).assertIsOff()

        newProcess()
        openSettings()
        node(SettingsTags.VOICE_SWITCH).assertIsOn()
        node(SettingsTags.SOUNDS_SWITCH).assertIsOff()

        node(SettingsTags.VOICE_SWITCH).performClick()
        node(SettingsTags.SOUNDS_SWITCH).performClick()
        newProcess()
        openSettings()
        node(SettingsTags.VOICE_SWITCH).assertIsOff()
        node(SettingsTags.SOUNDS_SWITCH).assertIsOn()
    }

    @Test
    @Config(qualifiers = "en")
    fun `AN13 AN25 call each dart is off, under the voice status, and kept after a new process`() {
        ShadowTextToSpeech.addVoice(TtsTestSupport.englishLocal)
        process.launchFresh()
        openSettings()
        node(SettingsTags.CALL_EACH_DART_SWITCH).assertIsOff().assertIsEnabled()
        composeRule.onNodeWithText("Needs the voice").assertDoesNotExist()

        val top = { tag: String -> node(tag).fetchSemanticsNode().boundsInRoot.top }
        val order = listOf(
            SettingsTags.VOICE_SWITCH,
            SettingsTags.VOICE_STATUS,
            SettingsTags.CALL_EACH_DART_SWITCH,
            SettingsTags.SOUNDS_SWITCH,
            SettingsTags.CREDITS,
        )
        assertEquals(order, order.sortedBy(top))

        node(SettingsTags.CALL_EACH_DART_SWITCH).performClick()
        newProcess()
        openSettings()
        node(SettingsTags.CALL_EACH_DART_SWITCH).assertIsOn()
    }

    @Test
    @Config(qualifiers = "en")
    fun `AN25 U10 with the voice off, call each dart is greyed, says why and keeps its value`() {
        ShadowTextToSpeech.addVoice(TtsTestSupport.englishLocal)
        process.launchFresh()
        openSettings()
        node(SettingsTags.CALL_EACH_DART_SWITCH).performClick()
        node(SettingsTags.VOICE_SWITCH).performClick()
        composeRule.waitForIdle()

        node(SettingsTags.CALL_EACH_DART_SWITCH).assertIsNotEnabled().assertIsOn()
        node(SettingsTags.CALL_EACH_DART_SWITCH).assert(hasText("Needs the voice", substring = true))
        node(SettingsTags.CALL_EACH_DART_SWITCH).performClick()
        node(SettingsTags.CALL_EACH_DART_SWITCH).assertIsOn()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `AN25 U10 the greyed switch says why in French`() {
        process.app.audioSettings.setVoiceEnabled(false)
        process.launchFresh()
        openSettings()

        node(SettingsTags.CALL_EACH_DART_SWITCH).assertIsNotEnabled()
            .assert(hasText("Annoncer chaque fléchette", substring = true))
            .assert(hasText("Nécessite la voix", substring = true))
    }

    @Test
    fun `AN14 switching the voice on with a usable voice says announcements on`() {
        ShadowTextToSpeech.addVoice(TtsTestSupport.englishLocal)
        process.launchFresh()
        openSettings()
        initLastEngine()
        composeRule.waitForIdle()
        node(SettingsTags.VOICE_STATUS).assertTextEquals(string(R.string.settings_voice_available))

        node(SettingsTags.VOICE_SWITCH).performClick()
        node(SettingsTags.VOICE_SWITCH).assertIsOff()
        node(SettingsTags.VOICE_SWITCH).performClick()
        composeRule.waitForIdle()

        node(SettingsTags.VOICE_SWITCH).assertIsOn()
        assertEquals(listOf("Announcements on"), spoken)
    }

    @Test
    fun `AN14 AN12 without a usable voice, the voice cannot be switched on and the status says why`() {
        ShadowTextToSpeech.addVoice(TtsTestSupport.englishNetwork)
        process.launchFresh()
        openSettings()
        initLastEngine()
        composeRule.waitForIdle()

        node(SettingsTags.VOICE_STATUS).assertTextEquals(string(R.string.settings_voice_missing))
        node(SettingsTags.VOICE_SWITCH).assertIsOn() // on by default, as stored
        node(SettingsTags.VOICE_SWITCH).performClick()
        node(SettingsTags.VOICE_SWITCH).performClick()
        composeRule.waitForIdle()

        node(SettingsTags.VOICE_SWITCH).assertIsOff()
        assertEquals(emptyList<String>(), spoken)
    }

    @Test
    fun `AN12 a voice that is not installed shows no voice`() {
        ShadowTextToSpeech.addVoice(TtsTestSupport.englishNotInstalled)
        process.launchFresh()
        openSettings()
        initLastEngine()
        composeRule.waitForIdle()

        node(SettingsTags.VOICE_STATUS).assertTextEquals(string(R.string.settings_voice_missing))
    }

    @Test
    fun `AN17 without an engine, the explanation shows without the button`() {
        process.launchFresh()
        openSettings()
        initLastEngine(TextToSpeech.ERROR)
        composeRule.waitForIdle()

        node(SettingsTags.VOICE_STATUS).assertTextEquals(string(R.string.settings_voice_no_engine))
        node(SettingsTags.INSTALL_VOICE).assertDoesNotExist()
    }

    @Test
    fun `AN17 the install button shows when the engine can install a voice, and opens its screen`() {
        registerInstallScreen("com.example.tts")
        ShadowTextToSpeech.addVoice(TtsTestSupport.englishNetwork)
        process.launchFresh()
        openSettings()
        initLastEngine()
        composeRule.waitForIdle()

        node(SettingsTags.INSTALL_VOICE).performScrollTo().performClick()

        val started = checkNotNull(shadowOf(process.activity).nextStartedActivity)
        assertEquals(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA, started.action)
        assertEquals("com.example.tts", started.`package`)
    }

    @Test
    fun `AN17 an engine without an install screen shows no button`() {
        audio.enginePackage = "com.example.tts"
        ShadowTextToSpeech.addVoice(TtsTestSupport.englishNetwork)
        process.launchFresh()
        openSettings()
        initLastEngine()
        composeRule.waitForIdle()

        node(SettingsTags.VOICE_STATUS).assertTextEquals(string(R.string.settings_voice_missing))
        node(SettingsTags.INSTALL_VOICE).assertDoesNotExist()
    }

    @Test
    fun `AN17 the voice is checked again when Settings is opened again or shown again`() {
        process.launchFresh()
        openSettings()
        val first = ShadowTextToSpeech.getLastTextToSpeechInstance()

        back()
        openSettings()
        val second = ShadowTextToSpeech.getLastTextToSpeechInstance()
        assertNotSame(first, second)
        assertTrue(shadowOf(checkNotNull(first)).isShutdown)

        process.stopActivity()
        process.restartActivity()
        composeRule.waitForIdle()
        assertNotSame(second, ShadowTextToSpeech.getLastTextToSpeechInstance())
        node(SettingsTags.VOICE_STATUS).assertTextEquals(string(R.string.settings_voice_checking))
    }

    @Test
    @Config(qualifiers = "en")
    fun `AN13 AN24 the elevenlabs mention is at the bottom, in English`() {
        process.launchFresh()
        openSettings()
        node(SettingsTags.CREDITS).performScrollTo().assertTextEquals("Sound effects generated with elevenlabs.io")
    }

    @Test
    @Config(qualifiers = "fr")
    fun `AN13 AN24 the elevenlabs mention is at the bottom, in French`() {
        process.launchFresh()
        openSettings()
        node(SettingsTags.CREDITS).performScrollTo().assertTextEquals("Effets sonores générés avec elevenlabs.io")
    }
}
