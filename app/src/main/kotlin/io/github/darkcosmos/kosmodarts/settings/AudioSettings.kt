package io.github.darkcosmos.kosmodarts.settings

/**
 * Sound settings of the device (AN14, AN15, AN16). Stored apart from the saved game (ADR 0004).
 *
 * @property noVoiceNoticeShown whether the "no offline voice" message was already shown once (AN16).
 * @property callEachDart whether the voice calls each dart (AN25); it only speaks while [voiceEnabled].
 */
data class AudioSettings(
    val voiceEnabled: Boolean = true,
    val soundsEnabled: Boolean = true,
    val noVoiceNoticeShown: Boolean = false,
    val callEachDart: Boolean = false,
)
