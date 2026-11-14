package io.github.darkcosmos.kosmodarts.engine.player

import kotlin.math.PI
import kotlin.random.Random

/** The color given to a new player (PC2) and the swatches offered to a player (PC14, PC15). */
object ColorChoices {
    private const val MIN_LIGHTNESS = 0.45
    private const val LIGHTNESS_RANGE = 0.5
    private const val MIN_CHROMA = 0.05
    private const val CHROMA_RANGE = 0.17
    private const val FULL_TURN = 2 * PI

    /**
     * PC2: the first palette color acceptable against [taken] (the colors of the other players), else a generated one.
     * Returns the first palette color when even generation fails, which cannot happen with at most 3 taken colors.
     */
    fun defaultFor(taken: List<PlayerColor>, random: Random = Random.Default): PlayerColor =
        PlayerColor.PALETTE.firstOrNull { PlayerColorRules.isAcceptable(it, taken) }
            ?: generate(taken, random)
            ?: PlayerColor.PALETTE.first()

    /**
     * PC14, PC15: the swatches for the player at [index] of [colors]: his own color, the acceptable palette colors,
     * then generated colors up to [PlayerColorRules.MIN_CHOICES]. Fewer when generation keeps failing.
     */
    fun choicesFor(index: Int, colors: List<PlayerColor>, random: Random): List<PlayerColor> {
        val own = colors[index]
        val others = colors.filterIndexed { i, _ -> i != index }
        val offered = mutableListOf(own)
        PlayerColor.PALETTE
            .filter { it != own && PlayerColorRules.isAcceptable(it, others) }
            .forEach { offered += it }
        while (offered.size < PlayerColorRules.MIN_CHOICES) {
            offered += generate(others + offered, random) ?: break
        }
        return offered
    }

    /** PC15: a random acceptable color against [avoid], or `null` after the attempts are used up. */
    private fun generate(avoid: List<PlayerColor>, random: Random): PlayerColor? {
        repeat(PlayerColorRules.MAX_GENERATION_ATTEMPTS) {
            val candidate = Oklab.toColorOrNull(
                lightness = MIN_LIGHTNESS + random.nextDouble() * LIGHTNESS_RANGE,
                chroma = MIN_CHROMA + random.nextDouble() * CHROMA_RANGE,
                hueRadians = random.nextDouble() * FULL_TURN,
            )
            if (candidate != null && PlayerColorRules.isAcceptable(candidate, avoid)) return candidate
        }
        return null
    }
}
