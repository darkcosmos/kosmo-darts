package io.github.darkcosmos.kosmodarts.capture.storage

import io.github.darkcosmos.kosmodarts.capture.session.CaptureCounters
import io.github.darkcosmos.kosmodarts.capture.session.CaptureSession
import io.github.darkcosmos.kosmodarts.capture.session.CaptureSetup
import io.github.darkcosmos.kosmodarts.capture.session.FakeClock
import io.github.darkcosmos.kosmodarts.capture.truth.AppInfo
import io.github.darkcosmos.kosmodarts.capture.truth.DeviceInfo
import io.github.darkcosmos.kosmodarts.capture.truth.EndReason
import io.github.darkcosmos.kosmodarts.capture.truth.PhotoSettings
import io.github.darkcosmos.kosmodarts.capture.truth.Resolution
import io.github.darkcosmos.kosmodarts.capture.truth.TimestampSource
import io.github.darkcosmos.kosmodarts.capture.truth.TruthDecodeResult
import io.github.darkcosmos.kosmodarts.capture.truth.TruthJson
import io.github.darkcosmos.kosmodarts.capture.truth.TruthSession
import io.github.darkcosmos.kosmodarts.capture.truth.VideoQuality
import io.github.darkcosmos.kosmodarts.capture.truth.VideoSettings
import io.github.darkcosmos.kosmodarts.capture.truth.ZoomRecord
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TruthWriterTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val setup = CaptureSetup(
        app = AppInfo("io.github.darkcosmos.kosmodarts.capture", "0.1.0"),
        device = DeviceInfo("Google", "Pixel 7", 37),
        video = VideoSettings(VideoQuality.UHD, Resolution(3840, 2160)),
        photo = PhotoSettings(Resolution(4080, 3072), TimestampSource.REALTIME),
        zoom = ZoomRecord(requestedRatio = 2.4f, ratio = 2.4f),
    )
    private val clock = FakeClock()
    private val storage = FakeCaptureStorage()
    private val settings by lazy { CaptureSettings(File(folder.root, "settings.txt")) }
    private val writer by lazy { TruthWriter(storage, settings) }
    private val entry = storage.createTruthFile("20261031-140322")

    private fun decoded(text: String): TruthSession =
        (TruthJson.decode(text) as TruthDecodeResult.Success).session

    /** Drives [session] and [counters] through an event and writes, as the screen does. */
    private class Run(var session: CaptureSession, var counters: CaptureCounters)

    private fun Run.apply(next: CaptureSession, writer: TruthWriter, entry: CaptureEntry): TruthWriteResult {
        session = next
        counters = counters.after(checkNotNull(next.truth))
        return writer.write(entry, checkNotNull(next.truth), counters)
    }

    private fun startedRun(): Run {
        val first = CaptureSession().start(clock, setup, emptySet()).session
        val run = Run(first, CaptureCounters().forNewSession())
        run.apply(first, writer, entry)
        return run
    }

    @Test
    fun `CP9 one write per event and the last write decodes to the final session`() {
        val run = startedRun()
        run.apply(run.session.dart(clock.advance(10), Dart.double(16)).session, writer, entry)
        run.apply(run.session.dart(clock.advance(10), Dart.single(20)).session, writer, entry)
        val stop = run.session.stop(clock.advance(10)).session
        val result = run.apply(stop, writer, entry)
        assertTrue(result.succeeded)
        assertEquals(4, storage.writes.size)
        assertEquals(checkNotNull(run.session.truth), decoded(storage.writes.last()))
    }

    @Test
    fun `CP9 a session killed mid volley leaves a file without end stamps`() {
        val run = startedRun()
        run.apply(run.session.dart(clock.advance(10), Dart.double(16)).session, writer, entry)
        val last = decoded(storage.writes.last())
        assertNull(last.endedAt)
        assertNull(last.endReason)
        assertNull(last.volleys.last().end)
        assertEquals(1, last.volleys.last().darts.size)
    }

    @Test
    fun `CP9 an interrupted session records endedAt and INTERRUPTED`() {
        val run = startedRun()
        run.apply(run.session.interrupt(clock.advance(10)).session, writer, entry)
        val last = decoded(storage.writes.last())
        assertNotNull(last.endedAt)
        assertEquals(EndReason.INTERRUPTED, last.endReason)
    }

    @Test
    fun `CP9 a failing truth write is reported and the total is still stored`() {
        val run = startedRun()
        storage.failWrites = true
        val result = run.apply(run.session.dart(clock.advance(10), Dart.single(1)).session, writer, entry)
        assertFalse(result.succeeded)
        assertNotNull(result.truthFailure)
        assertNull(result.totalFailure)
        assertEquals(1, settings.read().total)
    }

    @Test
    fun `CP7 a failing total write is reported and the truth is still written`() {
        val run = startedRun()
        val blocked = File(folder.root, "blocked")
        blocked.writeText("a file, not a directory")
        val failing = TruthWriter(storage, CaptureSettings(File(blocked, "settings.txt")))
        val before = storage.writes.size
        val result = run.apply(run.session.dart(clock.advance(10), Dart.single(1)).session, failing, entry)
        assertNotNull(result.totalFailure)
        assertNull(result.truthFailure)
        assertEquals(before + 1, storage.writes.size)
    }

    @Test
    fun `CP7 the total is persisted right after Undo, Stop and Interrupt`() {
        val run = startedRun()
        run.apply(run.session.dart(clock.advance(10), Dart.single(1)).session, writer, entry)
        run.apply(run.session.dart(clock.advance(10), Dart.single(2)).session, writer, entry)
        assertEquals(2, settings.read().total)
        run.apply(run.session.undo(clock.advance(10)).session, writer, entry)
        assertEquals(1, settings.read().total)
        run.apply(run.session.stop(clock.advance(10)).session, writer, entry)
        assertEquals(1, settings.read().total)

        val next = CaptureSession().start(clock.advance(1000), setup, emptySet()).session
        val second = Run(next, run.counters.forNewSession())
        second.apply(second.session.dart(clock.advance(10), Dart.single(3)).session, writer, entry)
        second.apply(second.session.interrupt(clock.advance(10)).session, writer, entry)
        assertEquals(2, settings.read().total)
    }
}
