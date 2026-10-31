package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.settings.AudioSettings

/** The "no offline voice" message of the game screen (AN16). [canInstall]: whether it shows "Install a voice". */
data class NoVoiceNotice(val canInstall: Boolean)

/**
 * AN16: the message shows on a game opened by Start or Rematch, with the voice on, if it was never shown on this
 * device. Pure.
 */
fun shouldShowNoVoiceNotice(screen: Screen, settings: AudioSettings): Boolean =
    screen.isNewGameShown && screen.noVoiceNoticeOrNull == null &&
        settings.voiceEnabled && !settings.noVoiceNoticeShown

/** The open "no offline voice" message of a game screen of any mode, or `null` (AN16). */
private val Screen.noVoiceNoticeOrNull: NoVoiceNotice?
    get() = when (this) {
        is Screen.Game -> noVoiceNotice
        is Screen.CricketGame -> noVoiceNotice
        else -> null
    }
