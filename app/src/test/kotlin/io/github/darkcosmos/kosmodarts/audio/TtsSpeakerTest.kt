package io.github.darkcosmos.kosmodarts.audio

import android.content.Context
import android.media.AudioManager
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.audio.TtsTestSupport.englishLocal
import io.github.darkcosmos.kosmodarts.audio.TtsTestSupport.englishNetwork
import io.github.darkcosmos.kosmodarts.audio.TtsTestSupport.englishNetworkSynthesis
import io.github.darkcosmos.kosmodarts.audio.TtsTestSupport.englishNotInstalled
import io.github.darkcosmos.kosmodarts.audio.TtsTestSupport.frenchLocal
import io.github.darkcosmos.kosmodarts.audio.TtsTestSupport.initLastEngine
import io.github.darkcosmos.kosmodarts.audio.TtsTestSupport.lastEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale

/**
 * The embedded-voice speaker over Robolectric's text-to-speech (docs/rules/announcements.md, AN11, AN12, AN18, AN20;
 * ADR 0003; findings S2, T3). The D5 tests are protected by canaries.
 */
@RunWith(AndroidJUnit4::class)
class TtsSpeakerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val ends = mutableListOf<String>()

    @After
    fun resetVoices() = ShadowTextToSpeech.reset()

    private fun speaker(language: Locale = Locale.ENGLISH, device: Locale = Locale.US) =
        TtsSpeaker(context, language, device, audioManager)

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun TtsSpeaker.say(text: String) = speak(text) { ends += text }

    @Test
    fun `AN12 a usable voice is set and speaks`() {
        ShadowTextToSpeech.addVoice(englishLocal)
        val speaker = speaker()
        initLastEngine()

        speaker.say("Ann")
        idle()

        assertEquals(VoiceStatus.AVAILABLE, speaker.status)
        assertEquals(englishLocal, lastEngine().currentVoice)
        assertEquals(listOf("Ann"), lastEngine().spokenTextList)
        assertEquals(listOf("Ann"), ends)
    }

    @Test
    fun `AN12 only a network voice - nothing is spoken and the voice is never set`() {
        ShadowTextToSpeech.addVoice(englishNetwork)
        val speaker = speaker()
        initLastEngine()

        speaker.say("Ann")
        idle()

        assertEquals(VoiceStatus.NO_USABLE_VOICE, speaker.status)
        assertNull(lastEngine().currentVoice)
        assertEquals(emptyList<String>(), lastEngine().spokenTextList)
        assertEquals(listOf("Ann"), ends)
    }

    @Test
    fun `AN12 only a voice that is not installed - nothing is spoken`() {
        ShadowTextToSpeech.addVoice(englishNotInstalled)
        val speaker = speaker()
        initLastEngine()
        speaker.say("Ann")

        assertEquals(VoiceStatus.NO_USABLE_VOICE, speaker.status)
        assertEquals(emptyList<String>(), lastEngine().spokenTextList)
    }

    @Test
    fun `AN12 S2 only a network synthesis voice - nothing is spoken`() {
        ShadowTextToSpeech.addVoice(englishNetworkSynthesis)
        val speaker = speaker()
        initLastEngine()
        speaker.say("Ann")

        assertEquals(VoiceStatus.NO_USABLE_VOICE, speaker.status)
        assertEquals(emptyList<String>(), lastEngine().spokenTextList)
    }

    @Test
    fun `AN11 a usable voice of another language is not used`() {
        ShadowTextToSpeech.addVoice(frenchLocal)
        val speaker = speaker(language = Locale.ENGLISH)
        initLastEngine()
        speaker.say("Ann")

        assertEquals(VoiceStatus.NO_USABLE_VOICE, speaker.status)
        assertEquals(emptyList<String>(), lastEngine().spokenTextList)
    }

    @Test
    fun `AN11 the French app speaks with the French voice`() {
        ShadowTextToSpeech.addVoice(englishLocal)
        ShadowTextToSpeech.addVoice(frenchLocal)
        speaker(language = Locale.FRENCH, device = Locale.FRANCE)
        initLastEngine()

        assertEquals(frenchLocal, lastEngine().currentVoice)
    }

    @Test
    fun `T3 texts asked for while the engine starts are held, only the latest, until a voice is set`() {
        ShadowTextToSpeech.addVoice(englishLocal)
        val speaker = speaker()
        speaker.say("first")
        speaker.say("latest")

        assertEquals(emptyList<String>(), lastEngine().spokenTextList)
        assertEquals(listOf("first"), ends)

        initLastEngine()
        idle()

        assertEquals(listOf("latest"), lastEngine().spokenTextList)
        assertEquals(listOf("first", "latest"), ends)
    }

    @Test
    fun `T3 AN12 a held text is skipped, never spoken, when only a network voice exists`() {
        ShadowTextToSpeech.addVoice(englishNetwork)
        val speaker = speaker()
        speaker.say("Ann")
        initLastEngine()
        idle()

        assertEquals(emptyList<String>(), lastEngine().spokenTextList)
        assertEquals(listOf("Ann"), ends)
    }

    @Test
    fun `T3 an engine that fails to start - nothing is spoken`() {
        ShadowTextToSpeech.addVoice(englishLocal)
        val speaker = speaker()
        speaker.say("Ann")
        initLastEngine(TextToSpeech.ERROR)

        assertEquals(VoiceStatus.NO_ENGINE, speaker.status)
        assertEquals(emptyList<String>(), lastEngine().spokenTextList)
        assertEquals(listOf("Ann"), ends)
    }

    @Test
    fun `AN20 refused focus skips the utterance`() {
        ShadowTextToSpeech.addVoice(englishLocal)
        val speaker = speaker()
        initLastEngine()
        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        speaker.say("Ann")

        assertEquals(emptyList<String>(), lastEngine().spokenTextList)
        assertEquals(listOf("Ann"), ends)
    }

    @Test
    fun `AN20 each utterance takes ducking focus and gives it back at its end`() {
        ShadowTextToSpeech.addVoice(englishLocal)
        val speaker = speaker()
        initLastEngine()

        speaker.say("Ann")
        val request = checkNotNull(shadowOf(audioManager).lastAudioFocusRequest)
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, request.durationHint)
        idle()

        assertEquals(request.audioFocusRequest, shadowOf(audioManager).lastAbandonedAudioFocusRequest)
        assertEquals(listOf("Ann"), ends)
    }

    @Test
    fun `AN18 an engine error ends the utterance once and silences the voice`() {
        ShadowTextToSpeech.addVoice(englishLocal)
        val speaker = speaker()
        initLastEngine()
        val statuses = mutableListOf<VoiceStatus>()
        speaker.onStatusChanged = { statuses += it }

        speaker.say("Ann")
        lastEngine().utteranceProgressListener.onError("announcement-0", TextToSpeech.ERROR_SYNTHESIS)
        idle()
        speaker.say("Bob")
        idle()

        assertEquals(listOf(VoiceStatus.NO_USABLE_VOICE), statuses)
        assertEquals(listOf("Ann"), lastEngine().spokenTextList)
        assertEquals(listOf("Ann", "Bob"), ends)
        assertTrue(shadowOf(audioManager).lastAbandonedAudioFocusRequest != null)
    }

    @Test
    fun `stop ends the utterance in progress once`() {
        ShadowTextToSpeech.addVoice(englishLocal)
        val speaker = speaker()
        initLastEngine()

        speaker.say("Ann")
        speaker.stop()
        idle()

        assertEquals(listOf("Ann"), ends)
        assertTrue(lastEngine().isStopped)
    }

    @Test
    fun `release shuts the engine down and later texts are skipped`() {
        ShadowTextToSpeech.addVoice(englishLocal)
        val speaker = speaker()
        initLastEngine()

        speaker.release()
        speaker.say("Ann")

        assertTrue(lastEngine().isShutdown)
        assertEquals(emptyList<String>(), lastEngine().spokenTextList)
        assertEquals(listOf("Ann"), ends)
    }
}
