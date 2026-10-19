package io.github.darkcosmos.kosmodarts.settings

import io.github.darkcosmos.kosmodarts.settings.AudioSettingsCodec.KEY_CALL_EACH_DART
import io.github.darkcosmos.kosmodarts.settings.AudioSettingsCodec.KEY_FORMAT_VERSION
import io.github.darkcosmos.kosmodarts.settings.AudioSettingsCodec.KEY_NO_VOICE_NOTICE_SHOWN
import io.github.darkcosmos.kosmodarts.settings.AudioSettingsCodec.KEY_SOUNDS_ENABLED
import io.github.darkcosmos.kosmodarts.settings.AudioSettingsCodec.KEY_VOICE_ENABLED
import org.junit.Assert.assertEquals
import org.junit.Test

/** Validated, versioned settings reads (AN15, ADR 0004 decision 2, finding S1). */
class AudioSettingsCodecTest {

    private val defaults = AudioSettings()

    private fun valid(voice: Boolean, sounds: Boolean, shown: Boolean): Map<String, Any?> = mapOf(
        KEY_FORMAT_VERSION to 1,
        KEY_VOICE_ENABLED to voice,
        KEY_SOUNDS_ENABLED to sounds,
        KEY_NO_VOICE_NOTICE_SHOWN to shown,
    )

    @Test
    fun `AN15 nothing stored gives the defaults, both switches on`() {
        assertEquals(AudioSettings(voiceEnabled = true, soundsEnabled = true, noVoiceNoticeShown = false), defaults)
        assertEquals(defaults, AudioSettingsCodec.decode(emptyMap()))
    }

    @Test
    fun `AN15 every combination round trips`() {
        listOf(true, false).forEach { voice ->
            listOf(true, false).forEach { sounds ->
                listOf(true, false).forEach { shown ->
                    val settings = AudioSettings(voice, sounds, shown)
                    assertEquals(settings, AudioSettingsCodec.decode(AudioSettingsCodec.encode(settings)))
                    assertEquals(settings, AudioSettingsCodec.decode(valid(voice, sounds, shown)))
                }
            }
        }
    }

    @Test
    fun `S1 a missing or unknown format version gives all defaults`() {
        val allOff = valid(voice = false, sounds = false, shown = true)
        listOf<Any?>(null, "1", 0, 2, 99, 1L, true).forEach { version ->
            val values = if (version == null) allOff - KEY_FORMAT_VERSION else allOff + (KEY_FORMAT_VERSION to version)
            assertEquals("version $version", defaults, AudioSettingsCodec.decode(values))
        }
    }

    @Test
    fun `S1 a wrongly typed value falls back to its own default only`() {
        listOf<Any?>("false", 0, 0L, null, 1.0f).forEach { wrong ->
            val values = valid(voice = false, sounds = false, shown = true) + (KEY_VOICE_ENABLED to wrong)
            assertEquals(
                "value $wrong",
                AudioSettings(voiceEnabled = true, soundsEnabled = false, noVoiceNoticeShown = true),
                AudioSettingsCodec.decode(values),
            )
        }
    }

    @Test
    fun `S1 a missing value falls back to its own default only`() {
        val values = valid(voice = false, sounds = false, shown = true) - KEY_NO_VOICE_NOTICE_SHOWN
        assertEquals(AudioSettings(voiceEnabled = false, soundsEnabled = false), AudioSettingsCodec.decode(values))
    }

    @Test
    fun `AN25 S8 a version 1 file without call_each_dart gives false and keeps the other keys`() {
        val old = valid(voice = false, sounds = false, shown = true)
        assertEquals(
            AudioSettings(voiceEnabled = false, soundsEnabled = false, noVoiceNoticeShown = true, callEachDart = false),
            AudioSettingsCodec.decode(old),
        )
    }

    @Test
    fun `AN25 S8 a wrongly typed call_each_dart gives false`() {
        listOf<Any?>("true", 1, null).forEach { wrong ->
            val values = valid(voice = true, sounds = true, shown = false) + (KEY_CALL_EACH_DART to wrong)
            assertEquals("value $wrong", AudioSettings(), AudioSettingsCodec.decode(values))
        }
    }

    @Test
    fun `AN25 call_each_dart round trips`() {
        val settings = AudioSettings(callEachDart = true)
        assertEquals(settings, AudioSettingsCodec.decode(AudioSettingsCodec.encode(settings)))
        assertEquals(true, AudioSettingsCodec.encode(settings)[KEY_CALL_EACH_DART])
    }

    @Test
    fun `S1 unknown keys are ignored`() {
        val values = valid(voice = false, sounds = true, shown = false) + ("future_key" to "anything")
        assertEquals(AudioSettings(voiceEnabled = false), AudioSettingsCodec.decode(values))
    }
}
