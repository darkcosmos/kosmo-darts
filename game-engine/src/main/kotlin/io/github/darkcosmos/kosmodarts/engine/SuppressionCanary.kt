package io.github.darkcosmos.kosmodarts.engine

/**
 * CI canary only (never merged): a deliberate warning suppression that CI must reject.
 */
object SuppressionCanary {
    // Suppression is the point of this canary: CI must reject it.
    @Suppress("MagicNumber")
    fun magicNumberProduct(): Int = 4242 * 17
}
