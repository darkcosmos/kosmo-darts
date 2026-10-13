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

    private val viewModel: KosmoDartsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark, so system bars always use light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsState()
            KosmoDartsTheme {
                KosmoDartsApp(state = state, onEvent = viewModel::onEvent)
            }
        }
    }
}
