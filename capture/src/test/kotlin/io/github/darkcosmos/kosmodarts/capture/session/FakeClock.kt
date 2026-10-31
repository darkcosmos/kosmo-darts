package io.github.darkcosmos.kosmodarts.capture.session

import java.time.Instant

/** Clock moved by hand; [advance] moves both clocks by the same milliseconds. */
internal class FakeClock(
    private var utc: Instant = Instant.parse("2026-10-31T14:03:22.123Z"),
    private var nanos: Long = 5_000_000_000L,
) : CaptureClock {
    override fun nowUtc(): Instant = utc

    override fun monotonicNanos(): Long = nanos

    fun advance(millis: Long): FakeClock {
        utc = utc.plusMillis(millis)
        nanos += millis * 1_000_000L
        return this
    }

    /** Sets the wall clock with sub-millisecond precision, to check the truncation. */
    fun setUtc(value: Instant): FakeClock {
        utc = value
        return this
    }
}
