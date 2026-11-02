package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor

// The `colors` line of format version 3 stores slot numbers 1 to 4, the positions in PlayerColor.PALETTE.
// Temporary mapping until format version 2 stores ARGB colors (plan of issue #51, task T4): only palette colors
// exist before the color picker.

/**
 * A `colors` value (R17, ADR 0006): one slot number per player, 1 to 4, separated by single spaces, all distinct
 * (PC1). A wrong count or an empty token is [InvalidSaveReason.MALFORMED]; a value outside 1 to 4 or a repeated slot
 * is [InvalidSaveReason.OUT_OF_RANGE].
 */
internal fun parseSlotColors(value: String, playerCount: Int): List<PlayerColor> {
    val tokens = value.split(' ')
    ensure(tokens.size == playerCount && tokens.none(String::isEmpty), InvalidSaveReason.MALFORMED)
    val colors = tokens.map {
        PlayerColor.PALETTE.getOrNull(SaveFormat.parseInteger(it) - 1) ?: reject(InvalidSaveReason.OUT_OF_RANGE)
    }
    ensure(colors.toSet().size == colors.size, InvalidSaveReason.OUT_OF_RANGE)
    return colors
}

/**
 * The `colors` value of [colors]: the slot number (1 to 4) of each palette color, separated by single spaces.
 *
 * @throws IllegalStateException for a color outside the palette.
 */
internal fun slotColorsValue(colors: List<PlayerColor>): String = colors.joinToString(" ") { color ->
    val index = PlayerColor.PALETTE.indexOf(color)
    check(index >= 0) { "Only palette colors are saved before format version 2" }
    (index + 1).toString()
}
