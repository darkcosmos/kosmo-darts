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
    }
}
