package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor
import java.util.Locale

/** An opaque ARGB color in the canonical form of the `colors` line: `FF` then 6 uppercase hex digits. */
private val COLOR_TOKEN = Regex("FF[0-9A-F]{6}")

private const val RADIX_HEX = 16

/**
 * A `colors` value (R17, R31, docs/adr/0013-save-format-v2-argb-colors.md): one ARGB color per player, 8 uppercase hex
 * digits with alpha `FF`, separated by single spaces, all distinct (PC1). A wrong count or an empty token is
 * [InvalidSaveReason.MALFORMED]; a token that is not an opaque canonical color, or a repeated color, is
 * [InvalidSaveReason.OUT_OF_RANGE].
 *
 * Only format, opacity and uniqueness are checked: PC11 to PC13 are not checked again on load, so a color that was
 * valid when chosen always resumes (D15).
 */
internal fun parseColors(value: String, playerCount: Int): List<PlayerColor> {
    val tokens = value.split(' ')
    ensure(tokens.size == playerCount && tokens.none(String::isEmpty), InvalidSaveReason.MALFORMED)
    val colors = tokens.map { token ->
        ensure(COLOR_TOKEN.matches(token), InvalidSaveReason.OUT_OF_RANGE)
        PlayerColor(token.toLong(RADIX_HEX).toInt())
    }
    ensure(colors.toSet().size == colors.size, InvalidSaveReason.OUT_OF_RANGE)
    return colors
}

/** The `colors` value of [colors]: each color as 8 uppercase hex digits (ARGB), separated by single spaces. */
internal fun colorsValue(colors: List<PlayerColor>): String = colors.joinToString(" ") { color ->
    String.format(Locale.ROOT, "%08X", color.argb)
}
