package io.github.darkcosmos.kosmodarts.audio

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale

/** Voices and engine callbacks for Robolectric tests over [ShadowTextToSpeech]. */
internal object TtsTestSupport {

    const val NOT_INSTALLED = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED

    /** Literal value of the deprecated `KEY_FEATURE_NETWORK_SYNTHESIS`. */
    const val NETWORK_SYNTHESIS = "networkTts"

    fun voice(
        name: String,
        locale: Locale,
        network: Boolean = false,
        features: Set<String>? = emptySet(),
        quality: Int = Voice.QUALITY_NORMAL,
    ) = Voice(name, locale, quality, Voice.LATENCY_NORMAL, network, features)

    val englishLocal = voice("en-us-local", Locale.US)
    val frenchLocal = voice("fr-fr-local", Locale.FRANCE)
    val englishNetwork = voice("en-us-network", Locale.US, network = true)
    val frenchNetwork = voice("fr-fr-network", Locale.FRANCE, network = true)
    val englishNotInstalled = voice("en-us-missing", Locale.US, features = setOf(NOT_INSTALLED))
    val englishNetworkSynthesis = voice("en-us-networktts", Locale.US, features = setOf(NETWORK_SYNTHESIS))

    /** A local voice in the language of the app's texts, as the current configuration resolves it. */
    fun localVoiceForTheAppLanguage(): Voice {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val language = context.getString(io.github.darkcosmos.kosmodarts.R.string.announce_language)
        return if (language == "fr") frenchLocal else englishLocal
    }

    /**
     * Declares the "install voice data" screen of a TTS engine [enginePackage], so that the explicit install intent
     * resolves (AN17).
     */
    fun registerInstallScreen(context: android.content.Context, enginePackage: String) {
        val component = android.content.ComponentName(enginePackage, "$enginePackage.InstallVoice")
        val packageManager = shadowOf(context.packageManager)
        packageManager.addActivityIfNotPresent(component)
        packageManager.addIntentFilterForActivity(
            component,
            android.content.IntentFilter(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                addCategory(android.content.Intent.CATEGORY_DEFAULT)
            },
        )
    }

    /** The engine of the last TextToSpeech created, as the shadow sees it. */
    fun lastEngine(): ShadowTextToSpeech = shadowOf(checkNotNull(ShadowTextToSpeech.getLastTextToSpeechInstance()))

    /** Finishes the engine's start, as Android does once it is bound. */
    fun initLastEngine(status: Int = TextToSpeech.SUCCESS) {
        lastEngine().onInitListener.onInit(status)
    }
}
