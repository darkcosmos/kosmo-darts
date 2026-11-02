package io.github.darkcosmos.kosmodarts

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.darkcosmos.kosmodarts.audio.AudioBinder
import io.github.darkcosmos.kosmodarts.audio.ResourcePhrases
import io.github.darkcosmos.kosmodarts.ui.KosmoDartsApp
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import io.github.darkcosmos.kosmodarts.ui.history.HistorySnapshot
import io.github.darkcosmos.kosmodarts.ui.settings.SettingsEvent
import io.github.darkcosmos.kosmodarts.ui.settings.SettingsViewModel
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme

class MainActivity : ComponentActivity() {

    /** Whether Android recreates this activity after the process was killed on the game screen (rule R1). */
    private var wasOnGameScreen = false

    private val app: KosmoDartsApplication
        get() = application as KosmoDartsApplication

    private val viewModel: KosmoDartsViewModel by viewModels {
        KosmoDartsViewModel.factory(app.persistence, app.audioSettings, wasOnGameScreen, restoredHistory)
    }

    /** The History filters and scroll position found in the saved instance state (HS6), or `null`. */
    private var restoredHistory: HistorySnapshot? = null

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.factory(app.audioSettings) { ResourcePhrases(app.resources) }
    }

    private lateinit var audioBinder: AudioBinder

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark, so system bars always use light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Read before the ViewModel is first used. The bundle only comes back when Android restores the task.
        wasOnGameScreen = savedInstanceState?.getBoolean(KEY_WAS_ON_GAME_SCREEN) == true
        restoredHistory = HistorySnapshot.decode(savedInstanceState?.getString(KEY_HISTORY))
        audioBinder = AudioBinder(this, viewModel, settingsViewModel, app.audioSettings, app.audioFactory)
        audioBinder.bind()
        setContent {
            val state by viewModel.state.collectAsState()
            val settings by settingsViewModel.state.collectAsState()
            KosmoDartsTheme {
                KosmoDartsApp(
                    state = state,
                    settings = settings,
                    onEvent = ::onAppEvent,
                    onSettingsEvent = ::onSettingsEvent,
                )
            }
        }
    }

    /**
     * Only this boolean and the History filters and scroll position (numbers and a mode name) are kept by Android,
     * never names or darts: the game itself is in the app's files, the matches in the database.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_WAS_ON_GAME_SCREEN, viewModel.isOnGameScreen)
        viewModel.historySnapshot?.let { outState.putString(KEY_HISTORY, it.encode()) }
    }

    /** "Install a voice" opens another app's screen (AN17); the rest is state. */
    private fun onAppEvent(event: AppEvent) {
        if (event == AppEvent.InstallVoiceTapped) audioBinder.installVoice()
        viewModel.onEvent(event)
    }

    private fun onSettingsEvent(event: SettingsEvent) {
        if (event == SettingsEvent.InstallVoiceTapped && !audioBinder.installVoice()) {
            // AN17: the engine's screen cannot open; the explanation stays, without the button.
            settingsViewModel.onInstallFailed()
        }
        settingsViewModel.onEvent(event)
    }

    private companion object {
        const val KEY_WAS_ON_GAME_SCREEN = "wasOnGameScreen"
        const val KEY_HISTORY = "history"
    }
}
