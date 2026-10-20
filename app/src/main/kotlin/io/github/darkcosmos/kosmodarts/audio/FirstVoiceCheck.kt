package io.github.darkcosmos.kosmodarts.audio

/**
 * Decides when a game audio session reports an unusable voice for the "no offline voice" message (AN16, AN18, A.4).
 * Pure.
 *
 * Only the first result of the session's voice check counts: a voice that fails later stays silent (AN18). The check
 * may end on the setup screen, where no message can open (A.4), so the result is kept and reported once a new game
 * screen is shown, and only once per session.
 */
class FirstVoiceCheck {
    private var firstResult: VoiceStatus? = null
    private var isReported = false

    /**
     * Records [status] (a status the speaker reported, in order) while [isNewGameShown] tells whether a game
     * opened by Start or Rematch is on screen. Returns `true` when the unusable voice must be reported now.
     */
    fun shouldReport(status: VoiceStatus, isNewGameShown: Boolean): Boolean {
        if (firstResult == null && status != VoiceStatus.CHECKING) firstResult = status
        val report = !isReported && isNewGameShown && firstResult?.isUnavailable == true
        if (report) isReported = true
        return report
    }
}
