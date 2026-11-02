package io.github.darkcosmos.kosmodarts.screenshots

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.GameMode
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the opponent tile names (issue #70): four players, French, on a Pixel 7. In landscape, a sets match
 * with Ann, Kevin, Kosmo and Maxime Dupont at font scales 1.3, 1.5 and 2.0; in portrait at 2.0, Ann, Bartholomew,
 * Maxime Dupont and Cid. No name breaks inside a word. Record only: see [HomeScreenScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TileNamesScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String, players: List<String>) {
        val viewModel = ViewModelProvider(composeRule.activity)[KosmoDartsViewModel::class.java]
        composeRule.runOnUiThread { startSetsMatch(players).forEach(viewModel::onEvent) }
        val directory = System.getProperty(SCREENSHOT_DIR_PROPERTY, DEFAULT_SCREENSHOT_DIR)
        composeRule.onRoot().captureRoboImage("$directory/$name.png")
    }

    @Test
    @Config(qualifiers = "+fr-land", fontScale = 1.3f)
    fun landscapeScale130() = capture("tile_names_4p_match_landscape_fr_font130", LANDSCAPE_PLAYERS)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = 1.5f)
    fun landscapeScale150() = capture("tile_names_4p_match_landscape_fr_font150", LANDSCAPE_PLAYERS)

    @Test
    @Config(qualifiers = "+fr-land", fontScale = 2.0f)
    fun landscapeScale200() = capture("tile_names_4p_match_landscape_fr_font200", LANDSCAPE_PLAYERS)

    @Test
    @Config(qualifiers = "+fr-port", fontScale = 2.0f)
    fun portraitScale200() = capture("tile_names_4p_portrait_fr_font200", PORTRAIT_PLAYERS)

    private companion object {
        const val SCREENSHOT_DIR_PROPERTY = "kosmodarts.screenshot.dir"
        const val DEFAULT_SCREENSHOT_DIR = "build/outputs/roborazzi"

        val LANDSCAPE_PLAYERS = listOf("Ann", "Kevin", "Kosmo", "Maxime Dupont")
        val PORTRAIT_PLAYERS = listOf("Ann", "Bartholomew", "Maxime Dupont", "Cid")

        /** A 301 sets match (3 legs, 3 sets) with four players, started and waiting for the first dart. */
        fun startSetsMatch(players: List<String>): List<AppEvent> = buildList {
            add(AppEvent.NewGame(GameMode.X01))
            add(AppEvent.StartScoreSelected(301))
            repeat(2) { add(AppEvent.PlayerAdded) }
            players.forEachIndexed { index, name -> add(AppEvent.NameChanged(index, name)) }
            repeat(2) { add(AppEvent.LegsPerSetChanged(1)) }
            repeat(2) { add(AppEvent.SetsChanged(1)) }
            add(AppEvent.StartRequested)
        }
    }
}
