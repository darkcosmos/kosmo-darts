package io.github.darkcosmos.kosmodarts.capture.session

import java.time.Instant

/** The two clocks of every timestamp (rule CP10), injected so tests control time. */
interface CaptureClock {
    /** Wall clock in UTC. */
    fun nowUtc(): Instant

    /** `SystemClock.elapsedRealtimeNanos()` on the phone. */
    fun monotonicNanos(): Long
}
