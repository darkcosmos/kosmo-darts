package io.github.darkcosmos.kosmodarts.audio

/** A clock and delayed actions on the main thread. Replaced by a virtual clock in tests. */
interface Scheduler {
    fun now(): Long

    fun post(delayMillis: Long, action: () -> Unit): Cancellable
}

fun interface Cancellable {
    fun cancel()
}
