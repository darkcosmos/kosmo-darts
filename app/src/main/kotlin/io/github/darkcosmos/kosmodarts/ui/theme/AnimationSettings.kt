package io.github.darkcosmos.kosmodarts.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Duration of the only animation of the app: the zone color fade on a player change (design system, section 7). */
const val ZONE_FADE_MILLIS: Int = 200

/**
 * Whether Android animations are on, read from the system setting ("Remove animations" turns them off). Provided by a
 * composition local so that tests and captures can turn them off.
 */
fun interface AnimationSettings {
    fun areAnimationsEnabled(): Boolean

    companion object {
        /** The system setting. */
        val System: AnimationSettings = AnimationSettings { ValueAnimator.areAnimatorsEnabled() }
    }
}

/** The animation setting of the current composition. */
val LocalAnimationSettings = staticCompositionLocalOf { AnimationSettings.System }

/** Section 7: a 200 ms fade, or an immediate change when animations are off. */
fun zoneColorAnimationSpec(enabled: Boolean): AnimationSpec<Color> =
    if (enabled) tween(durationMillis = ZONE_FADE_MILLIS) else snap()
