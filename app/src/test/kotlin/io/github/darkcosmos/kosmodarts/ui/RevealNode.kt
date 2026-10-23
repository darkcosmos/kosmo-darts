package io.github.darkcosmos.kosmodarts.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performScrollTo

/**
 * Scrolls this node into view when one of its ancestors scrolls, and does nothing otherwise. The game screen only
 * scrolls below the score floor of the layout policy (plan of issue #49, section 4b, step 4), for example on the small
 * default Robolectric screen, so its tests reveal a node before touching it whatever the screen size.
 */
fun SemanticsNodeInteraction.revealIfScrolled(): SemanticsNodeInteraction {
    var parent = fetchSemanticsNode().parent
    while (parent != null) {
        if (parent.config.contains(SemanticsActions.ScrollBy)) return performScrollTo()
        parent = parent.parent
    }
    return this
}
