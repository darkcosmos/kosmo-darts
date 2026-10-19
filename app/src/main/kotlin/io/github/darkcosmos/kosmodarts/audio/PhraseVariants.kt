package io.github.darkcosmos.kosmodarts.audio

import kotlin.random.Random

/** Chooses one index among [allowed] (never empty). Tests use a fixed choice (AN26). */
fun interface VariantPicker {
    fun pick(allowed: List<Int>): Int
}

/** A random choice. */
class RandomVariantPicker(private val random: Random = Random.Default) : VariantPicker {
    override fun pick(allowed: List<Int>): Int = allowed.random(random)
}

/**
 * Draws a variant of each phrase line, never the one drawn last time for the same line (AN26). Lives as long as the
 * game audio session (see Known limitations of the plan). Pure.
 */
class PhraseVariants(private val picker: VariantPicker) {
    private val lastDrawn = mutableMapOf<String, Int>()

    /** An index in `0 until count` for [line]; with one variant, always 0. */
    fun next(line: String, count: Int): Int {
        require(count > 0) { "A phrase line needs at least one variant" }
        val allowed = (0 until count).filter { count == 1 || it != lastDrawn[line] }
        val drawn = picker.pick(allowed)
        require(drawn in allowed) { "The picker must choose among the allowed indices" }
        lastDrawn[line] = drawn
        return drawn
    }
}
