package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.persistence.GameLoad
import io.github.darkcosmos.kosmodarts.persistence.LoadResult
import io.github.darkcosmos.kosmodarts.persistence.SaveFixtures
import io.github.darkcosmos.kosmodarts.settings.AudioSettings
import io.github.darkcosmos.kosmodarts.settings.AudioSettingsRepository
import io.github.darkcosmos.kosmodarts.settings.AudioSettingsStore
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

/** The "no offline voice" message (docs/rules/announcements.md, AN16; finding T1). */
class NoVoiceNoticeTest {

    /** Records the order of the flag write and of the state changes. */
    private val log = mutableListOf<String>()

    private inner class LoggingStore(private var settings: AudioSettings) : AudioSettingsStore {
        override fun read() = settings

        override fun write(settings: AudioSettings) {
            log += "stored noVoiceNoticeShown=${settings.noVoiceNoticeShown}"
            this.settings = settings
        }
    }

    private val config = X01Config(listOf("Ann", "Bob"))

    private fun viewModel(initial: AppState, settings: AudioSettings = AudioSettings()): KosmoDartsViewModel {
        val viewModel = KosmoDartsViewModel(Random(0), initial, AudioSettingsRepository(LoggingStore(settings)))
        viewModel.onScreenChanged = { log += "screen ${it::class.simpleName}" }
        return viewModel
    }

    private val KosmoDartsViewModel.notice: NoVoiceNotice?
        get() = (state.value.screen as? Screen.Game)?.noVoiceNotice

    private fun started(settings: AudioSettings = AudioSettings()): KosmoDartsViewModel =
        viewModel(AppState(screen = Screen.Setup(SetupState.prefilled(config))), settings).apply {
            onEvent(AppEvent.StartRequested)
        }

    @Test
    fun `AN16 after start, with the voice on, the message opens with the install choice`() {
        listOf(true, false).forEach { canInstall ->
            val viewModel = started()
            viewModel.onEvent(AppEvent.VoiceUnavailable(canInstall))
            assertEquals(NoVoiceNotice(canInstall), viewModel.notice)
        }
    }

    @Test
    fun `AN16 after a rematch, the message opens`() {
        val viewModel = viewModel(AppState(screen = Screen.End(SaveFixtures.WAITING_CHECKOUT.game)))
        viewModel.onEvent(AppEvent.Rematch)
        viewModel.onEvent(AppEvent.VoiceUnavailable(canInstall = true))
        assertEquals(NoVoiceNotice(true), viewModel.notice)
    }

    @Test
    fun `AN16 after a resume from home, no message`() {
        val viewModel = viewModel(AppState(screen = Screen.Home(resumableGame = SaveFixtures.MID_TURN)))
        viewModel.onEvent(AppEvent.ResumeTapped)
        viewModel.onEvent(AppEvent.VoiceUnavailable(canInstall = true))
        assertNull(viewModel.notice)
    }

    @Test
    fun `AN16 on a game restored after process death, no message`() {
        val load = LoadResult(GameLoad.Valid(SaveFixtures.MID_TURN), lastSetup = null)
        val viewModel = viewModel(initialState(load, wasOnGameScreen = true))
        viewModel.onEvent(AppEvent.VoiceUnavailable(canInstall = true))
        assertNull(viewModel.notice)
    }

    @Test
    fun `AN16 with the voice off, no message`() {
        val viewModel = started(AudioSettings(voiceEnabled = false))
        viewModel.onEvent(AppEvent.VoiceUnavailable(canInstall = true))
        assertNull(viewModel.notice)
    }

    @Test
    fun `AN16 once shown on this device, never again`() {
        val viewModel = started(AudioSettings(noVoiceNoticeShown = true))
        viewModel.onEvent(AppEvent.VoiceUnavailable(canInstall = true))
        assertNull(viewModel.notice)
    }

    @Test
    fun `AN16 the flag is stored before the message opens, and the message waits for an answer`() {
        val viewModel = started()
        log.clear()
        viewModel.onEvent(AppEvent.VoiceUnavailable(canInstall = true))

        assertEquals(listOf("stored noVoiceNoticeShown=true"), log)
        assertEquals(NoVoiceNotice(true), viewModel.notice)
    }

    @Test
    fun `AN16 OK, back and install close it, and it does not open again in the same game`() {
        listOf(AppEvent.NoVoiceNoticeDismissed, AppEvent.Back, AppEvent.InstallVoiceTapped).forEach { close ->
            val viewModel = started()
            viewModel.onEvent(AppEvent.VoiceUnavailable(canInstall = true))
            viewModel.onEvent(close)
            assertNull("$close", viewModel.notice)
            assertEquals("$close", Screen.Game::class, viewModel.state.value.screen::class)

            viewModel.onEvent(AppEvent.VoiceUnavailable(canInstall = true))
            assertNull("$close", viewModel.notice)
        }
    }

    @Test
    fun `AN16 while the message is open, the keypad does nothing`() {
        val viewModel = started()
        viewModel.onEvent(AppEvent.VoiceUnavailable(canInstall = false))
        val open = viewModel.state.value

        viewModel.onEvent(AppEvent.NumberTapped(20))
        viewModel.onEvent(AppEvent.UndoTapped)

        assertEquals(open, viewModel.state.value)
    }

    @Test
    fun `AN16 the message is not saved`() {
        val viewModel = started()
        val without = savedGameOf(viewModel.state.value)
        viewModel.onEvent(AppEvent.VoiceUnavailable(canInstall = true))
        assertEquals(without, savedGameOf(viewModel.state.value))
    }

    @Test
    fun `AN16 outside the game screen, the event changes nothing`() {
        val home = AppState(screen = Screen.Home())
        val viewModel = viewModel(home)
        viewModel.onEvent(AppEvent.VoiceUnavailable(canInstall = true))
        assertEquals(home, viewModel.state.value)
        assertEquals(emptyList<String>(), log)
    }
}
