package io.github.darkcosmos.kosmodarts.audio

import io.github.darkcosmos.kosmodarts.audio.VoiceStatus.AVAILABLE
import io.github.darkcosmos.kosmodarts.audio.VoiceStatus.CHECKING
import io.github.darkcosmos.kosmodarts.audio.VoiceStatus.NO_ENGINE
import io.github.darkcosmos.kosmodarts.audio.VoiceStatus.NO_USABLE_VOICE
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a session reports an unusable voice (docs/rules/announcements.md, AN16, AN18, A.4; finding T24). */
class FirstVoiceCheckTest {

    private val check = FirstVoiceCheck()

    @Test
    fun `A4 a result found during setup is reported when the new game screen opens`() {
        assertFalse(check.shouldReport(NO_USABLE_VOICE, isNewGameShown = false)) // on setup
        assertTrue(check.shouldReport(NO_USABLE_VOICE, isNewGameShown = true)) // Start
    }

    @Test
    fun `AN16 a result found on the new game screen is reported at once`() {
        assertFalse(check.shouldReport(CHECKING, isNewGameShown = true))
        assertTrue(check.shouldReport(NO_ENGINE, isNewGameShown = true))
    }

    @Test
    fun `AN16 it is reported once per session, not again on a rematch or a second game`() {
        assertTrue(check.shouldReport(NO_USABLE_VOICE, isNewGameShown = true))
        assertFalse(check.shouldReport(NO_USABLE_VOICE, isNewGameShown = false)) // end screen, setup
        assertFalse(check.shouldReport(NO_USABLE_VOICE, isNewGameShown = true)) // rematch or second game
    }

    @Test
    fun `AN16 a usable voice is never reported`() {
        assertFalse(check.shouldReport(AVAILABLE, isNewGameShown = false))
        assertFalse(check.shouldReport(AVAILABLE, isNewGameShown = true))
    }

    @Test
    fun `AN18 a voice that fails after a usable first result is not reported`() {
        assertFalse(check.shouldReport(AVAILABLE, isNewGameShown = false)) // ready during setup
        assertFalse(check.shouldReport(NO_USABLE_VOICE, isNewGameShown = true)) // fails during the game
        assertFalse(check.shouldReport(NO_USABLE_VOICE, isNewGameShown = true))
    }

    @Test
    fun `AN16 a resumed game is never a new game, so nothing is reported`() {
        assertFalse(check.shouldReport(NO_USABLE_VOICE, isNewGameShown = false))
        assertFalse(check.shouldReport(NO_USABLE_VOICE, isNewGameShown = false))
    }
}
