package io.github.darkcosmos.kosmodarts.capture.session

import io.github.darkcosmos.kosmodarts.capture.truth.AppInfo
import io.github.darkcosmos.kosmodarts.capture.truth.DeviceInfo
import io.github.darkcosmos.kosmodarts.capture.truth.PhotoSettings
import io.github.darkcosmos.kosmodarts.capture.truth.Resolution
import io.github.darkcosmos.kosmodarts.capture.truth.TimestampSource
import io.github.darkcosmos.kosmodarts.capture.truth.VideoQuality
import io.github.darkcosmos.kosmodarts.capture.truth.VideoSettings
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureCountersTest {

    private val setup = CaptureSetup(
        AppInfo("id", "1"),
        DeviceInfo("Google", "Pixel 7", 37),
        VideoSettings(VideoQuality.FHD, Resolution(1920, 1080)),
        PhotoSettings(Resolution(4080, 3072), TimestampSource.UNKNOWN),
    )
    private val clock = FakeClock()

    private fun track(counters: CaptureCounters, outcome: CaptureOutcome): CaptureCounters =
        counters.after(checkNotNull(outcome.session.truth))

    @Test
    fun `CP7 counters ignore voided darts and track undo`() {
        var session = CaptureSession().start(clock, setup, emptySet()).session
        var counters = CaptureCounters().forNewSession().after(checkNotNull(session.truth))
        repeat(2) {
            val outcome = session.dart(clock, Dart(20))
            session = outcome.session
            counters = track(counters, outcome)
        }
        assertEquals(CaptureCounters(session = 2, total = 2), counters)
        val undone = session.undo(clock)
        counters = track(counters, undone)
        assertEquals(CaptureCounters(session = 1, total = 1), counters)
    }

    @Test
    fun `CP7 the total keeps earlier sessions and the session counter restarts`() {
        val counters = CaptureCounters(session = 4, total = 10).forNewSession()
        assertEquals(CaptureCounters(session = 0, total = 10), counters)
        val session = CaptureSession().start(clock, setup, emptySet()).session
        val after = counters.after(checkNotNull(session.dart(clock, Dart(1)).session.truth))
        assertEquals(CaptureCounters(session = 1, total = 11), after)
    }

    @Test
    fun `CP7 Stop and Interrupt do not change the counters`() {
        val session = CaptureSession().start(clock, setup, emptySet()).session.dart(clock, Dart(1)).session
        val counters = CaptureCounters().after(checkNotNull(session.truth))
        assertEquals(counters, counters.after(checkNotNull(session.stop(clock).session.truth)))
        assertEquals(counters, counters.after(checkNotNull(session.interrupt(clock).session.truth)))
    }

    @Test
    fun `CP8 cleared resets both counters`() {
        assertEquals(CaptureCounters(), CaptureCounters(3, 9).cleared())
    }
}
