package io.github.darkcosmos.kosmodarts.capture.session

import android.os.SystemClock
import java.time.Instant

/** The phone's clocks: UTC wall clock and `SystemClock.elapsedRealtimeNanos()` (rule CP10). */
object SystemCaptureClock : CaptureClock {
    override fun nowUtc(): Instant = Instant.now()

    override fun monotonicNanos(): Long = SystemClock.elapsedRealtimeNanos()
}
