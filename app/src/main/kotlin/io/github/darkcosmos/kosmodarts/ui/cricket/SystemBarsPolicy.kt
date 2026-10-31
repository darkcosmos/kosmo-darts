package io.github.darkcosmos.kosmodarts.ui.cricket

import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/** Which insets pad the screen's content. */
enum class InsetsPadding {
    /** Bars, display cutout and keyboard: the bars are shown. */
    SAFE_DRAWING,

    /** The display cutout only: the bars are hidden, and come back over the content after a swipe. */
    CUTOUT_ONLY,
}

/** Whether the system bars are hidden, and the padding that goes with it: "hide" always pads the cutout only. */
data class SystemBarsDecision(val isHidden: Boolean) {
    val padding: InsetsPadding
        get() = if (isHidden) InsetsPadding.CUTOUT_ONLY else InsetsPadding.SAFE_DRAWING
}

/**
 * The system bars of a screen (docs/rules/cricket.md CR22, plan section 4, Kosmo's Q5 B): only the Cricket game in
 * landscape hides them, so its 7 rows keep 48 dp on the Pixel 7. Pure, tested on the JVM.
 */
object SystemBarsPolicy {
    fun decide(isLandscape: Boolean, isCricketGame: Boolean): SystemBarsDecision =
        SystemBarsDecision(isHidden = isLandscape && isCricketGame)
}

/** Hides and shows the system bars; the window implementation is [WindowSystemBarsController], tests use a fake. */
interface SystemBarsController {
    fun hide()

    fun show()
}

/**
 * The platform `WindowInsetsController` of the window that holds [view] (API 30, the `minSdk`). After a swipe from the
 * edge the hidden bars come back for a moment over the content
 * ([WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE]). The display cutout mode stays the one
 * `enableEdgeToEdge` sets in `MainActivity`.
 */
class WindowSystemBarsController(private val view: View) : SystemBarsController {
    override fun hide() {
        view.windowInsetsController?.let {
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsets.Type.systemBars())
        }
    }

    override fun show() {
        view.windowInsetsController?.show(WindowInsets.Type.systemBars())
    }
}

/** The controller [SystemBarsEffect] uses; `null` (production) means the window's [WindowSystemBarsController]. */
val LocalSystemBarsController = staticCompositionLocalOf<SystemBarsController?> { null }

/**
 * The only place of the app that hides the system bars: applies [decision] and, when it leaves the composition (turn
 * to portrait, between legs, end of match, abandon, home, the activity destroyed), shows the bars again.
 */
@Composable
fun SystemBarsEffect(decision: SystemBarsDecision) {
    val view = LocalView.current
    val window = remember(view) { WindowSystemBarsController(view) }
    val controller = LocalSystemBarsController.current ?: window
    DisposableEffect(decision, controller) {
        if (decision.isHidden) controller.hide() else controller.show()
        onDispose { controller.show() }
    }
}
