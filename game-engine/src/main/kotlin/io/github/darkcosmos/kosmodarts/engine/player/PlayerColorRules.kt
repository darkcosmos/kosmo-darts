package io.github.darkcosmos.kosmodarts.engine.player

/** Player color rules PC10 to PC13 (docs/rules/players.md); thresholds are named constants (PC17). */
object PlayerColorRules {
    /** PC10: the app background `#0A0F1E`. */
    val BACKGROUND = PlayerColor(0xFF0A0F1E.toInt())

    /** PC10: the tile surface `#121A33`. */
    val SURFACE = PlayerColor(0xFF121A33.toInt())

    /** PC13: white text. */
    val TEXT_LIGHT = PlayerColor(0xFFFFFFFF.toInt())

    /** PC13: dark text, the background color. */
    val TEXT_DARK = BACKGROUND

    const val MIN_COLOR_CONTRAST = 3.0
    const val MIN_TEXT_CONTRAST = 4.5
    const val MIN_COLOR_DISTANCE = 0.12
    const val MIN_CHOICES = 4
    const val MAX_GENERATION_ATTEMPTS = 200

    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722
    private const val LUMINANCE_OFFSET = 0.05

    private fun luminance(color: PlayerColor): Double {
        val (r, g, b) = Oklab.linearRgb(color)
        return RED_WEIGHT * r + GREEN_WEIGHT * g + BLUE_WEIGHT * b
    }

    /** WCAG contrast ratio, from 1 (same color) to 21 (white on black); symmetric. */
    fun contrastRatio(a: PlayerColor, b: PlayerColor): Double {
        val first = luminance(a)
        val second = luminance(b)
        return (maxOf(first, second) + LUMINANCE_OFFSET) / (minOf(first, second) + LUMINANCE_OFFSET)
    }

    /** OKLab Euclidean distance (PC12). */
    fun distance(a: PlayerColor, b: PlayerColor): Double = Oklab.distance(Oklab.fromColor(a), Oklab.fromColor(b))

    /** PC11: readable against the background and the surface. */
    fun isReadable(color: PlayerColor): Boolean =
        contrastRatio(color, BACKGROUND) >= MIN_COLOR_CONTRAST && contrastRatio(color, SURFACE) >= MIN_COLOR_CONTRAST

    /** PC13: white or dark text, whichever contrasts more (white on a tie). */
    fun textOn(color: PlayerColor): PlayerColor =
        if (contrastRatio(color, TEXT_LIGHT) >= contrastRatio(color, TEXT_DARK)) TEXT_LIGHT else TEXT_DARK

    /** PC13: the text color reaches [MIN_TEXT_CONTRAST]. */
    private fun hasReadableText(color: PlayerColor): Boolean =
        contrastRatio(color, textOn(color)) >= MIN_TEXT_CONTRAST

    /**
     * PC12: [color] satisfies PC11, PC13 and is at least [MIN_COLOR_DISTANCE] from every color of [others].
     * The single check behind `defaultFor`, `choicesFor` and the generator.
     */
    fun isAcceptable(color: PlayerColor, others: List<PlayerColor>): Boolean =
        isReadable(color) && hasReadableText(color) && others.all { distance(color, it) >= MIN_COLOR_DISTANCE }
}
