package io.github.darkcosmos.kosmodarts.audio

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech

/**
 * Opens the TTS engine's own "install voice data" screen (AN17): the engine downloads the voice, not the app. The
 * intent is explicit (the engine's package) and has no extras.
 */
class VoiceInstaller(private val context: Context, enginePackage: String?) {

    private val intent: Intent? = enginePackage?.takeIf { it.isNotBlank() }?.let {
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(it)
    }

    /** Whether an activity can answer the intent. Without an engine, `false`. */
    val canInstall: Boolean
        get() = intent != null && context.packageManager.resolveActivity(intent, 0) != null

    /** Opens the engine's screen. Returns `false`, without crashing, when it cannot be opened. */
    fun launch(): Boolean {
        val target = intent ?: return false
        return try {
            context.startActivity(target)
            true
        } catch (ignored: ActivityNotFoundException) {
            // AN17: the caller shows the explanation without the button.
            false
        } catch (ignored: SecurityException) {
            // Same: an engine screen we may not open is treated as absent.
            false
        }
    }
}
