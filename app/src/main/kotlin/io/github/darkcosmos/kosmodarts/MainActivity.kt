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
import io.github.darkcosmos.kosmodarts.ui.KosmoDartsApp
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoDartsTheme

class MainActivity : ComponentActivity() {

    /** Whether Android recreates this activity after the process was killed on the game screen (rule R1). */
    private var wasOnGameScreen = false

    private val viewModel: KosmoDartsViewModel by viewModels {
        KosmoDartsViewModel.factory((application as KosmoDartsApplication).persistence, wasOnGameScreen)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark, so system bars always use light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Read before the ViewModel is first used. The bundle only comes back when Android restores the task.
        wasOnGameScreen = savedInstanceState?.getBoolean(KEY_WAS_ON_GAME_SCREEN) == true
        setContent {
            val state by viewModel.state.collectAsState()
            KosmoDartsTheme {
                KosmoDartsApp(state = state, onEvent = viewModel::onEvent)
            }
        }
    }

    /** Only this boolean is kept by Android, never names or darts: the game itself is in the app's files. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_WAS_ON_GAME_SCREEN, viewModel.isOnGameScreen)
    }

    private companion object {
        const val KEY_WAS_ON_GAME_SCREEN = "wasOnGameScreen"
    }
}
