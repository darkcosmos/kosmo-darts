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
    private val warmUpFile = java.io.File(context.cacheDir, TtsSpeaker.WARM_UP_FILE)

    private fun warmSpeaker() = TtsSpeaker(context, Locale.ENGLISH, Locale.US, audioManager, warmUp = true)

    @Test
    fun `A6 after the voice is set, one silent warm-up to the cache file, nothing spoken`() {
        ShadowTextToSpeech.addVoice(englishLocal)
        val speaker = warmSpeaker()
        assertNull(lastEngine().lastSynthesizeToFileText)

        initLastEngine()

        assertEquals("0", lastEngine().lastSynthesizeToFileText)
        assertEquals(warmUpFile, lastEngine().lastSynthesizeToFile)
        assertEquals(emptyList<String>(), lastEngine().spokenTextList)
        assertNull("no focus for the warm-up", shadowOf(audioManager).lastAudioFocusRequest)
        assertEquals(VoiceStatus.AVAILABLE, speaker.status)
    }

    @Test
    fun `A6 T27 no warm-up with only a network voice, nor without an engine`() {
        ShadowTextToSpeech.addVoice(englishNetwork)
        warmSpeaker()
        initLastEngine()
        assertNull(lastEngine().lastSynthesizeToFileText)

        warmSpeaker()
        initLastEngine(TextToSpeech.ERROR)
        assertNull(lastEngine().lastSynthesizeToFileText)
    }

    @Test
    fun `A6 the warm-up end deletes the file and ends no phrase`() {
        ShadowTextToSpeech.addVoice(englishLocal)
        val speaker = warmSpeaker()
        val statuses = mutableListOf<VoiceStatus>()
        speaker.onStatusChanged = { statuses += it }
        initLastEngine()
        assertTrue(warmUpFile.exists())

        lastEngine().utteranceProgressListener.onDone(TtsSpeaker.WARM_UP_ID)
        idle()

        assertTrue(!warmUpFile.exists())
        assertEquals(emptyList<String>(), ends)
        assertEquals(emptyList<VoiceStatus>(), statuses.drop(1))
        assertNull(shadowOf(audioManager).lastAbandonedAudioFocusRequest)
    }

    @Test
    fun `A6 S12 a warm-up error neither silences the voice nor ends a phrase, and a real error still does`() {
        ShadowTextToSpeech.addVoice(englishLocal)
        val speaker = warmSpeaker()
        initLastEngine()
        idle()
        lastEngine().utteranceProgressListener.onError(TtsSpeaker.WARM_UP_ID, TextToSpeech.ERROR_SYNTHESIS)
        idle()

        assertEquals(VoiceStatus.AVAILABLE, speaker.status)
        assertEquals(emptyList<String>(), ends)
        assertTrue(!warmUpFile.exists())

        speaker.say("Ann")
        lastEngine().utteranceProgressListener.onError("announcement-0", TextToSpeech.ERROR_SYNTHESIS)
        idle()
        assertEquals(VoiceStatus.NO_USABLE_VOICE, speaker.status)
    }

    @Test
    fun `A6 a phrase during the warm-up is spoken once and the warm-up stop is ignored`() {
        ShadowTextToSpeech.addVoice(englishLocal)
        val speaker = warmSpeaker()
        initLastEngine()
        speaker.say("Ann")
        lastEngine().utteranceProgressListener.onStop(TtsSpeaker.WARM_UP_ID, true)
        idle()

        assertEquals(listOf("Ann"), lastEngine().spokenTextList)
        assertEquals(listOf("Ann"), ends)
        assertEquals(VoiceStatus.AVAILABLE, speaker.status)
        assertTrue(!warmUpFile.exists())
        assertEquals(TextToSpeech.QUEUE_FLUSH, lastEngine().queueMode)
    }

    @Test
    fun `A6 T29 one warm-up per engine, never retried nor sent per phrase`() {
        ShadowTextToSpeech.addVoice(englishLocal)
        val speaker = warmSpeaker()
        initLastEngine()
        lastEngine().utteranceProgressListener.onDone(TtsSpeaker.WARM_UP_ID)
        idle()
        assertTrue(!warmUpFile.exists())

        // A second warm-up would write the file again at once (the engine reports its end later).
        speaker.say("Ann")
        assertTrue(!warmUpFile.exists())
        speaker.stop()
        speaker.say("Bob")
        assertTrue(!warmUpFile.exists())
    }

    @Test
    fun `A6 a phrase already waiting at start makes the warm-up useless, so none is sent`() {
        ShadowTextToSpeech.addVoice(englishLocal)
        val speaker = warmSpeaker()
        speaker.say("Ann")
        initLastEngine()

        assertNull(lastEngine().lastSynthesizeToFileText)
        assertEquals(listOf("Ann"), lastEngine().spokenTextList)
    }

    @Test
    fun `A6 a failed warm-up leaves no file and the voice usable`() {
        ShadowTextToSpeech.addVoice(englishLocal)
        val speaker = TtsSpeaker(context, Locale.ENGLISH, Locale.US, audioManager, warmUp = true)
        lastEngine().simulateSynthesizeToFileResult(TextToSpeech.ERROR)
        initLastEngine()
        idle()

        assertTrue(!warmUpFile.exists())
        assertEquals(VoiceStatus.AVAILABLE, speaker.status)
    }

    @Test
    fun `A6 release during the warm-up leaves no file and no callback`() {
        ShadowTextToSpeech.addVoice(englishLocal)
        val speaker = warmSpeaker()
        initLastEngine()
        speaker.release()
        idle()

        assertTrue(!warmUpFile.exists())
        assertEquals(emptyList<String>(), ends)
    }

    @Test
    fun `A6 S11 a leftover warm-up file is deleted when a speaker is created`() {
        warmUpFile.writeText("leftover")
        TtsSpeaker(context, Locale.ENGLISH, Locale.US, audioManager)

        assertTrue(!warmUpFile.exists())
    }

    @Test
    fun `A6 T26 the Settings probe does not warm up and leaves no file`() {
        ShadowTextToSpeech.addVoice(englishLocal)
        val probe = speaker() // warmUp = false
        initLastEngine()
        probe.release()

        assertNull(lastEngine().lastSynthesizeToFileText)
        assertTrue(!warmUpFile.exists())
    }
}
