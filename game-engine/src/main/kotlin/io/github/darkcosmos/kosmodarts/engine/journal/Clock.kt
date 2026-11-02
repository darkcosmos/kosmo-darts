package io.github.darkcosmos.kosmodarts.engine.journal

/** Source of entry times, injected so that the engine stays pure and testable (DJ1). */
fun interface Clock {
    /** Milliseconds since the epoch. */
    fun nowMillis(): Long
}
