package io.github.darkcosmos.kosmodarts.ui.components

import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor

/** What TalkBack says of a swatch (PC14, PC15): the name of a palette color, or "Color n of m" for a generated one. */
sealed interface SwatchName {
    data class Palette(val color: PlayerColor) : SwatchName

    data class Generated(val position: Int, val count: Int) : SwatchName
}

/** [position] is 1-based among the [count] swatches of the row. */
fun swatchName(color: PlayerColor, position: Int, count: Int): SwatchName =
    if (color in PlayerColor.PALETTE) SwatchName.Palette(color) else SwatchName.Generated(position, count)
