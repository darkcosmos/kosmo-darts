package io.github.darkcosmos.kosmodarts.audio

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** "Install a voice" (docs/rules/announcements.md, AN17; finding S3). */
@RunWith(AndroidJUnit4::class)
class VoiceInstallerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun registerInstallScreen(enginePackage: String) {
        TtsTestSupport.registerInstallScreen(context, enginePackage)
    }

    private class Throwing(base: Context, private val error: RuntimeException) : ContextWrapper(base) {
        override fun startActivity(intent: Intent) = throw error
    }

    /** The app passes its activity; this stands for it and records what it starts. */
    private class Recording(base: Context) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            started += intent
        }
    }

    @Test
    fun `AN17 without an engine, no install`() {
        assertFalse(VoiceInstaller(context, null).canInstall)
        assertFalse(VoiceInstaller(context, " ").canInstall)
        assertFalse(VoiceInstaller(context, null).launch())
    }

    @Test
    fun `AN17 an engine without an install screen, no install`() {
        assertFalse(VoiceInstaller(context, "com.example.tts").canInstall)
    }

    @Test
    fun `AN17 the engine's install screen is opened explicitly, without extras`() {
        registerInstallScreen("com.example.tts")
        val activity = Recording(context)
        val installer = VoiceInstaller(activity, "com.example.tts")

        assertTrue(installer.canInstall)
        assertTrue(installer.launch())

        val started = activity.started.single()
        assertEquals(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA, started.action)
        assertEquals("com.example.tts", started.`package`)
        assertEquals(null, started.extras)
    }

    @Test
    fun `AN17 S3 a launch that fails never crashes`() {
        listOf(ActivityNotFoundException(), SecurityException()).forEach { error ->
            assertFalse(VoiceInstaller(Throwing(context, error), "com.example.tts").launch())
        }
    }
}
