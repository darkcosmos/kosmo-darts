package io.github.darkcosmos.kosmodarts.ui.theme

import androidx.compose.ui.graphics.Color
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColorRules

/**
 * Colors of the players (docs/rules/players.md, PC9, PC13): a player is drawn with his own [PlayerColor], and text on
 * it with the text color the engine picks for it. The four palette colors equal the tokens `playerN` and `onPlayerN`.
 * Pure: no composition needed, so it is tested on the JVM.
 */
object PlayerPalette {

    /** The Compose color of [color]. */
    fun colorOf(color: PlayerColor): Color = Color(color.argb)

    /** PC13: text and icons drawn on [colorOf], white or dark, whichever contrasts more. */
    fun onColorOf(color: PlayerColor): Color = Color(PlayerColorRules.textOn(color).argb)
}
