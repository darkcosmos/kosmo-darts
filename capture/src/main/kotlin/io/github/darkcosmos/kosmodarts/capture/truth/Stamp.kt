package io.github.darkcosmos.kosmodarts.capture.truth

import java.time.Instant

private const val NANOS_PER_MILLI = 1_000_000

/**
 * When an event happened, on both clocks of the truth file (rule CP10).
 *
 * [utc] is the wall clock, with a whole number of milliseconds (truncate with
 * `Instant.truncatedTo(ChronoUnit.MILLIS)`), between [UtcFormat.MIN] and [UtcFormat.MAX]. [monotonicNanos] is
 * `SystemClock.elapsedRealtimeNanos()`, never negative. [videoPositionMs] is the position of the event in the current
 * volley video, never negative, or null before the video `Start` event; it is always null for the start and end of
 * the session and for the start of a volley.
 */
data class Stamp(val utc: Instant, val monotonicNanos: Long, val videoPositionMs: Long? = null) {

    init {
        require(utc.nano % NANOS_PER_MILLI == 0) { "utc $utc is more precise than the millisecond" }
        require(utc in UtcFormat.MIN..UtcFormat.MAX) { "utc $utc is outside the years 0000 to 9999" }
        require(monotonicNanos >= 0) { "monotonicNanos $monotonicNanos is negative" }
        require(videoPositionMs == null || videoPositionMs >= 0) { "videoPositionMs $videoPositionMs is negative" }
    }
}
