package io.github.darkcosmos.kosmodarts.engine.player

/**
 * An opaque player color as ARGB (docs/rules/players.md, PC1, PC10 to PC15).
 *
 * @throws IllegalArgumentException if the alpha channel is not `FF`. Stored values go through [fromStored].
 */
@JvmInline
value class PlayerColor(val argb: Int) {
    init {
        require(argb ushr ALPHA_SHIFT == OPAQUE_ALPHA) { "A player color must be opaque" }
    }

    companion object {
        private const val ALPHA_SHIFT = 24
        private const val OPAQUE_ALPHA = 0xFF
        private const val RGB_MASK = 0x00FFFFFF

        val BLUE = PlayerColor(0xFF1F63D6.toInt())
        val ORANGE = PlayerColor(0xFFE07020.toInt())
        val TEAL = PlayerColor(0xFF0C7C6A.toInt())
        val LILAC = PlayerColor(0xFFC9A7FF.toInt())

        /** The four palette colors, in the order they are given (PC2). */
        val PALETTE: List<PlayerColor> = listOf(BLUE, ORANGE, TEAL, LILAC)

        /** A stored value read back: the alpha is forced to `FF`, never an exception (D16). */
        fun fromStored(argb: Int): PlayerColor = PlayerColor((OPAQUE_ALPHA shl ALPHA_SHIFT) or (argb and RGB_MASK))

        /**
         * The palette colors of positions 1 to [count]: the default of a match without explicit colors (PC5).
         *
         * @throws IllegalArgumentException if [count] is not from 1 to the palette size.
         */
        fun byPosition(count: Int): List<PlayerColor> {
            require(count in 1..PALETTE.size) { "count must be from 1 to ${PALETTE.size}" }
            return PALETTE.take(count)
        }
    }
}

/** Thrown when a list of colors does not give each player his own color (PC1, PC5). */
class InvalidPlayerColorsException(message: String) : IllegalArgumentException(message)

/**
 * Checks PC1 and PC5: exactly [playerCount] colors, all distinct.
 *
 * @throws InvalidPlayerColorsException otherwise.
 */
fun requireValidColors(colors: List<PlayerColor>, playerCount: Int) {
    if (colors.size != playerCount) throw InvalidPlayerColorsException("Expected $playerCount colors")
    if (colors.toSet().size != colors.size) throw InvalidPlayerColorsException("Two players share a color")
}
