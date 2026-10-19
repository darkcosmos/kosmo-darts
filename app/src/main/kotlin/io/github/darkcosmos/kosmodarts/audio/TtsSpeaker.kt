package io.github.darkcosmos.kosmodarts.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import java.util.MissingResourceException

/**
 * [Speaker] over Android's text-to-speech engine, with an embedded voice only (AN11, AN12, AN18, AN20, ADR 0003):
 * - the voice is chosen by [VoiceChoice] among the engine's voices and set with `setVoice`; `setLanguage` is never
 *   called, since the engine could then pick a network voice;
 * - no text is sent to the engine before that voice is set; a text asked for while the engine starts is held (only
 *   the latest one) until the voice is known;
 * - each utterance holds transient, ducking audio focus; a refused focus skips it.
 *
 * @param language the app language, as the app's texts are shown (AN11).
 * @param deviceLocale the phone's locale: its country is used only to prefer one voice of [language], and only if
 *   it has that language.
 */
class TtsSpeaker(
    context: Context,
    language: Locale,
    deviceLocale: Locale,
    private val audioManager: AudioManager,
) : Speaker {

    private val appLanguage = iso3Language(language)
    private val appCountry = if (iso3Language(deviceLocale) == appLanguage) iso3Country(deviceLocale) else ""
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = mutableMapOf<String, Utterance>()
    private var held: Held? = null
    private var nextUtteranceId = 0
    private var isReleased = false

    override var status: VoiceStatus = VoiceStatus.CHECKING
        private set

    override var onStatusChanged: (VoiceStatus) -> Unit = {}

    private val tts = TextToSpeech(context, ::onInit)

    override val enginePackage: String?
        get() = if (isReleased) null else tts.defaultEngine

    override fun speak(text: String, onEnd: () -> Unit) {
        when {
            isReleased -> onEnd()
            status == VoiceStatus.CHECKING -> {
                // Only the latest text waits: an older one is skipped, so that its caller moves on.
                held?.onEnd?.invoke()
                held = Held(text, onEnd)
            }
            status == VoiceStatus.AVAILABLE -> utter(text, onEnd)
            else -> onEnd()
        }
    }

    override fun stop() {
        held?.let {
            held = null
            it.onEnd()
        }
        if (pending.isEmpty()) return
        tts.stop()
        pending.keys.toList().forEach { finish(it, isError = false) }
    }

    override fun release() {
        if (isReleased) return
        stop()
        isReleased = true
        onStatusChanged = {}
        tts.shutdown()
    }

    private fun onInit(initStatus: Int) {
        if (isReleased) return
        val next = if (initStatus == TextToSpeech.SUCCESS) selectVoice() else VoiceStatus.NO_ENGINE
        setStatus(next)
        held?.let {
            held = null
            speak(it.text, it.onEnd)
        }
    }

    private fun selectVoice(): VoiceStatus {
        val voices = try {
            tts.voices.orEmpty()
        } catch (ignored: RuntimeException) {
            // Some engines throw when they cannot list their voices: there is then no usable voice (AN12).
            emptySet<Voice>()
        }
        val candidates = voices.associateBy(::candidateOf)
        val chosen = VoiceChoice.choose(candidates.keys, appLanguage, appCountry)?.let(candidates::get)
        val isSet = chosen != null && tts.setVoice(chosen) == TextToSpeech.SUCCESS
        if (isSet) {
            tts.setAudioAttributes(AUDIO_ATTRIBUTES)
            tts.setOnUtteranceProgressListener(progressListener)
        }
        return if (isSet) VoiceStatus.AVAILABLE else VoiceStatus.NO_USABLE_VOICE
    }

    private fun utter(text: String, onEnd: () -> Unit) {
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AUDIO_ATTRIBUTES)
            .build()
        if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // AN20: skipped, never played later.
            onEnd()
            return
        }
        val id = "announcement-${nextUtteranceId++}"
        pending[id] = Utterance(focus, onEnd)
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) finish(id, isError = true)
    }

    /** Ends utterance [id] once: gives the focus back, then calls its callback. Later calls do nothing. */
    private fun finish(id: String, isError: Boolean) {
        val utterance = pending.remove(id) ?: return
        audioManager.abandonAudioFocusRequest(utterance.focus)
        if (isError && !isReleased) {
            // AN18: silent from now on, without a message. Only the id is logged, never the text.
            Log.w(TAG, "Utterance $id failed; the voice is off for this session")
            setStatus(VoiceStatus.NO_USABLE_VOICE)
        }
        utterance.onEnd()
    }

    private fun setStatus(next: VoiceStatus) {
        if (next == status) return
        status = next
        onStatusChanged(next)
    }

    /** The engine calls back on its own thread; everything else runs on the main thread. */
    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) = Unit

        override fun onDone(utteranceId: String) {
            mainHandler.post { finish(utteranceId, isError = false) }
        }

        override fun onStop(utteranceId: String, interrupted: Boolean) {
            mainHandler.post { finish(utteranceId, isError = false) }
        }

        @Deprecated("Deprecated in the framework; still called by engines that do not report an error code.")
        override fun onError(utteranceId: String) {
            mainHandler.post { finish(utteranceId, isError = true) }
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            mainHandler.post { finish(utteranceId, isError = true) }
        }
    }

    private class Utterance(val focus: AudioFocusRequest, val onEnd: () -> Unit)

    private class Held(val text: String, val onEnd: () -> Unit)

    companion object {
        private const val TAG = "TtsSpeaker"

        /**
         * Value of `TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS`, deprecated since API 21 but still reported by
         * engines in a voice's features. Written as a literal so that rejecting such voices (AN12) needs no deprecated
         * API and no warning suppression.
         */
        private const val FEATURE_NETWORK_SYNTHESIS = "networkTts"

        /** Media volume (AN19), like the sound effects. */
        val AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        fun candidateOf(voice: Voice): VoiceCandidate {
            val features = voice.features.orEmpty()
            return VoiceCandidate(
                name = voice.name,
                language = iso3Language(voice.locale),
                country = iso3Country(voice.locale),
                quality = voice.quality,
                isNetworkConnectionRequired = voice.isNetworkConnectionRequired,
                isNotInstalled = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in features,
                isNetworkSynthesis = FEATURE_NETWORK_SYNTHESIS in features,
            )
        }

        private fun iso3Language(locale: Locale): String = try {
            locale.isO3Language
        } catch (ignored: MissingResourceException) {
            // Unknown code: compared as given.
            locale.language
        }

        private fun iso3Country(locale: Locale): String = try {
            locale.isO3Country
        } catch (ignored: MissingResourceException) {
            // Unknown code: compared as given.
            locale.country
        }
    }
}
