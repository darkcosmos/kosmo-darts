package io.github.darkcosmos.kosmodarts.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.KosmoDartsApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Settings stored apart from the saved game, read without trusting the file (AN15, ADR 0004; finding S1). */
@RunWith(AndroidJUnit4::class)
class SharedPreferencesAudioSettingsStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun preferences() =
        context.getSharedPreferences(SharedPreferencesAudioSettingsStore.FILE_NAME, Context.MODE_PRIVATE)

    private fun store() = SharedPreferencesAudioSettingsStore(preferences())

    @Test
    fun `AN15 what is written is read back by a new store`() {
        val settings = AudioSettings(voiceEnabled = false, soundsEnabled = true, noVoiceNoticeShown = true)
        store().write(settings)
        assertEquals(settings, store().read())
    }

    @Test
    fun `AN15 an empty file gives the defaults`() {
        assertEquals(AudioSettings(), store().read())
    }

    @Test
    fun `S1 a wrongly typed value gives its default, without an exception`() {
        store().write(AudioSettings(voiceEnabled = false, soundsEnabled = false))
        preferences().edit().putString(AudioSettingsCodec.KEY_SOUNDS_ENABLED, "nope").commit()

        assertEquals(AudioSettings(voiceEnabled = false, soundsEnabled = true), store().read())
    }

    @Test
    fun `S1 a newer format version gives all defaults`() {
        store().write(AudioSettings(voiceEnabled = false, soundsEnabled = false))
        preferences().edit().putInt(AudioSettingsCodec.KEY_FORMAT_VERSION, 2).commit()

        assertEquals(AudioSettings(), store().read())
    }

    @Test
    fun `AN15 the settings live in their own file and never touch the game files`() {
        val gameFile = File(context.filesDir, KosmoDartsApplication.GAME_FILE)
        val setupFile = File(context.filesDir, KosmoDartsApplication.SETUP_FILE)

        store().write(AudioSettings(soundsEnabled = false))

        assertFalse(gameFile.exists())
        assertFalse(setupFile.exists())
        assertEquals("audio_settings", SharedPreferencesAudioSettingsStore.FILE_NAME)
    }
}
