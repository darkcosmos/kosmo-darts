package io.github.darkcosmos.kosmodarts.screenshots

import android.speech.tts.TextToSpeech
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.audio.TestAudioFactory
import io.github.darkcosmos.kosmodarts.audio.TtsTestSupport
import io.github.darkcosmos.kosmodarts.audio.TtsTestSupport.initLastEngine
import io.github.darkcosmos.kosmodarts.ui.home.HomeTags
import io.github.darkcosmos.kosmodarts.ui.resume.AppProcess
import io.github.darkcosmos.kosmodarts.ui.setup.SetupTags
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowTextToSpeech

/**
 * Screenshots of the Settings screen and of the "no offline voice" message (docs/rules/announcements.md, AN13,
 * AN16, AN17), on a Pixel 7. Record only: see [HomeScreenScreenshotTest]. The home screen with its gear and the game
 * screen without any sound control are captured by the existing home and X01 screenshot tests.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AnnouncementsScreenshotTest {

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

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        composeRule.waitForIdle()
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        captureScreenRoboImage("$directory/$name.png")
    }

    private fun canInstall() {
        val engine = "com.example.tts"
        audio.enginePackage = engine
        TtsTestSupport.registerInstallScreen(process.app, engine)
    }

    private fun settings(name: String, voice: android.speech.tts.Voice?, initStatus: Int? = TextToSpeech.SUCCESS) {
        voice?.let(ShadowTextToSpeech::addVoice)
        process.launchFresh()
        composeRule.onNodeWithTag(HomeTags.SETTINGS).performClick()
        composeRule.waitForIdle()
        initStatus?.let(::initLastEngine)
        capture(name)
    }

    private fun voiceAvailable(name: String) = settings(name, TtsTestSupport.localVoiceForTheAppLanguage())

    private fun noVoiceWithInstall(name: String) {
        canInstall()
        settings(name, TtsTestSupport.englishNetwork)
    }

    private fun noVoiceWithoutInstall(name: String) = settings(name, TtsTestSupport.englishNetwork)

    private fun noEngine(name: String) = settings(name, voice = null, initStatus = TextToSpeech.ERROR)

    private fun checking(name: String) = settings(name, voice = null, initStatus = null)

    private fun noVoiceMessage(name: String) {
        canInstall()
        ShadowTextToSpeech.addVoice(TtsTestSupport.englishNetwork)
        process.launchFresh()
        composeRule.onNodeWithText(process.string(R.string.home_new_game)).performClick()
        composeRule.onNodeWithTag(SetupTags.nameField(0)).performTextReplacement("Ann")
        composeRule.onNodeWithTag(SetupTags.nameField(1)).performTextReplacement("Bob")
        composeRule.onNodeWithText(process.string(R.string.setup_start)).performScrollTo().performClick()
        composeRule.waitForIdle()
        initLastEngine()
        capture(name)
    }

    /** AN25, U10: the voice off: "Call each dart" greyed with "Needs the voice", its value (on) kept. */
    private fun callsGreyed(name: String) {
        process.app.audioSettings.setCallEachDart(true)
        process.app.audioSettings.setVoiceEnabled(false)
        settings(name, TtsTestSupport.localVoiceForTheAppLanguage())
    }

    /** AN25: the three switches on. */
    private fun threeSwitchesOn(name: String) {
        process.app.audioSettings.setCallEachDart(true)
        settings(name, TtsTestSupport.localVoiceForTheAppLanguage())
    }

    @Test
    @Config(qualifiers = "+en-port")
    fun settingsCallsGreyedPortraitEnglish() = callsGreyed("settings_calls_greyed_portrait_en")

    @Test
    @Config(qualifiers = "+en-land")
    fun settingsCallsGreyedLandscapeEnglish() = callsGreyed("settings_calls_greyed_landscape_en")

    @Test
    @Config(qualifiers = "+fr-port")
    fun settingsCallsGreyedPortraitFrench() = callsGreyed("settings_calls_greyed_portrait_fr")

    @Test
    @Config(qualifiers = "+fr-land")
    fun settingsCallsGreyedLandscapeFrench() = callsGreyed("settings_calls_greyed_landscape_fr")

    @Test
    @Config(qualifiers = "+en-port")
    fun settingsThreeSwitchesPortraitEnglish() = threeSwitchesOn("settings_three_switches_portrait_en")

    @Test
    @Config(qualifiers = "+en-land")
    fun settingsThreeSwitchesLandscapeEnglish() = threeSwitchesOn("settings_three_switches_landscape_en")

    @Test
    @Config(qualifiers = "+fr-port")
    fun settingsThreeSwitchesPortraitFrench() = threeSwitchesOn("settings_three_switches_portrait_fr")

    @Test
    @Config(qualifiers = "+fr-land")
    fun settingsThreeSwitchesLandscapeFrench() = threeSwitchesOn("settings_three_switches_landscape_fr")

    @Test
    @Config(qualifiers = "+en-port")
    fun settingsVoiceAvailablePortraitEnglish() = voiceAvailable("settings_voice_available_portrait_en")

    @Test
    @Config(qualifiers = "+en-land")
    fun settingsVoiceAvailableLandscapeEnglish() = voiceAvailable("settings_voice_available_landscape_en")

    @Test
    @Config(qualifiers = "+fr-port")
    fun settingsVoiceAvailablePortraitFrench() = voiceAvailable("settings_voice_available_portrait_fr")

    @Test
    @Config(qualifiers = "+fr-land")
    fun settingsVoiceAvailableLandscapeFrench() = voiceAvailable("settings_voice_available_landscape_fr")

    @Test
    @Config(qualifiers = "+en-port")
    fun settingsNoVoiceInstallPortraitEnglish() = noVoiceWithInstall("settings_no_voice_install_portrait_en")

    @Test
    @Config(qualifiers = "+en-land")
    fun settingsNoVoiceInstallLandscapeEnglish() = noVoiceWithInstall("settings_no_voice_install_landscape_en")

    @Test
    @Config(qualifiers = "+fr-port")
    fun settingsNoVoiceInstallPortraitFrench() = noVoiceWithInstall("settings_no_voice_install_portrait_fr")

    @Test
    @Config(qualifiers = "+fr-land")
    fun settingsNoVoiceInstallLandscapeFrench() = noVoiceWithInstall("settings_no_voice_install_landscape_fr")

    @Test
    @Config(qualifiers = "+en-port")
    fun settingsNoVoicePortraitEnglish() = noVoiceWithoutInstall("settings_no_voice_portrait_en")

    @Test
    @Config(qualifiers = "+en-land")
    fun settingsNoVoiceLandscapeEnglish() = noVoiceWithoutInstall("settings_no_voice_landscape_en")

    @Test
    @Config(qualifiers = "+fr-port")
    fun settingsNoVoicePortraitFrench() = noVoiceWithoutInstall("settings_no_voice_portrait_fr")

    @Test
    @Config(qualifiers = "+en-port")
    fun settingsNoEnginePortraitEnglish() = noEngine("settings_no_engine_portrait_en")

    @Test
    @Config(qualifiers = "+fr-port")
    fun settingsNoEnginePortraitFrench() = noEngine("settings_no_engine_portrait_fr")

    @Test
    @Config(qualifiers = "+en-port")
    fun settingsCheckingPortraitEnglish() = checking("settings_checking_portrait_en")

    @Test
    @Config(qualifiers = "+en-port")
    fun noVoiceMessagePortraitEnglish() = noVoiceMessage("game_no_voice_message_portrait_en")

    @Test
    @Config(qualifiers = "+en-land")
    fun noVoiceMessageLandscapeEnglish() = noVoiceMessage("game_no_voice_message_landscape_en")

    @Test
    @Config(qualifiers = "+fr-port")
    fun noVoiceMessagePortraitFrench() = noVoiceMessage("game_no_voice_message_portrait_fr")

    @Test
    @Config(qualifiers = "+fr-land")
    fun noVoiceMessageLandscapeFrench() = noVoiceMessage("game_no_voice_message_landscape_fr")

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"
    }
}
