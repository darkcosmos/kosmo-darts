package io.github.darkcosmos.kosmodarts.capture.session

import io.github.darkcosmos.kosmodarts.capture.truth.AppInfo
import io.github.darkcosmos.kosmodarts.capture.truth.DeviceInfo
import io.github.darkcosmos.kosmodarts.capture.truth.EndReason
import io.github.darkcosmos.kosmodarts.capture.truth.PhotoSettings
import io.github.darkcosmos.kosmodarts.capture.truth.Resolution
import io.github.darkcosmos.kosmodarts.capture.truth.TimestampSource
import io.github.darkcosmos.kosmodarts.capture.truth.TruthSession
import io.github.darkcosmos.kosmodarts.capture.truth.VideoQuality
import io.github.darkcosmos.kosmodarts.capture.truth.VideoSettings
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSessionTest {

    private val setup = CaptureSetup(
        app = AppInfo("io.github.darkcosmos.kosmodarts.capture", "0.1.0"),
        device = DeviceInfo("Google", "Pixel 7", 37),
        video = VideoSettings(VideoQuality.UHD, Resolution(3840, 2160)),
        photo = PhotoSettings(Resolution(4080, 3072), TimestampSource.REALTIME),
    )
    private val clock = FakeClock()
    private val t20 = Dart(20, Multiplier.TRIPLE)

    private fun started(): CaptureSession = CaptureSession().start(clock, setup, emptySet()).session

    private fun CaptureSession.withDarts(count: Int): CaptureSession =
        (1..count).fold(this) { session, _ -> session.dart(clock.advance(10), t20).session }

    private fun CaptureSession.data(): TruthSession = checkNotNull(truth)

    @Test
    fun `CP1 Start creates the folder, volley 1 and its video`() {
        val outcome = CaptureSession().start(clock, setup, emptySet())
        val truth = outcome.session.data()
        assertEquals("20261031-140322", truth.session)
        assertEquals("volley-001.mp4", truth.volleys.single().video)
        assertEquals(listOf<CaptureEffect>(CaptureEffect.StartVideo(1, "volley-001.mp4")), outcome.effects)
        assertTrue(outcome.session.running)
        assertNull(truth.endedAt)
    }

    @Test
    fun `CP1 Start twice in the same second gets the collision suffix`() {
        val outcome = CaptureSession().start(clock, setup, setOf("20261031-140322"))
        assertEquals("20261031-140322-2", outcome.session.data().session)
    }

    @Test
    fun `CP1 Start while running is refused`() {
        val running = started()
        val outcome = running.start(clock, setup, emptySet())
        assertEquals(CaptureRefusal.ALREADY_RUNNING, outcome.refusal)
        assertSame(running, outcome.session)
    }

    @Test
    fun `CP10 times are truncated to whole milliseconds`() {
        clock.setUtc(Instant.parse("2026-10-31T14:03:22.123456789Z"))
        val truth = started().data()
        assertEquals(Instant.parse("2026-10-31T14:03:22.123Z"), truth.startedAt.utc)
    }

    @Test
    fun `CP2 Dart, Undo, NextVolley, Stop and Interrupt are refused outside a session`() {
        val idle = CaptureSession()
        val refusals = listOf(
            idle.dart(clock, t20), idle.undo(clock), idle.nextVolley(clock), idle.stop(clock), idle.interrupt(clock),
        ).map { it.refusal }
        assertTrue(refusals.all { it == CaptureRefusal.NO_SESSION })
        val ended = started().stop(clock).session
        assertEquals(CaptureRefusal.NO_SESSION, ended.dart(clock, t20).refusal)
        assertEquals(CaptureRefusal.NO_SESSION, ended.undo(clock).refusal)
        assertEquals(CaptureRefusal.NO_SESSION, ended.nextVolley(clock).refusal)
    }

    @Test
    fun `CP3 Dart appends an entry and asks for one photo`() {
        val outcome = started().dart(clock.advance(500), t20)
        val dart = outcome.session.data().volleys.single().darts.single()
        assertEquals(1, dart.number)
        assertEquals(t20, dart.dart)
        assertNull(dart.photo)
        assertEquals(listOf<CaptureEffect>(CaptureEffect.TakePhoto(1, "dart-001.jpg")), outcome.effects)
    }

    @Test
    fun `CP3 the fourth non voided dart is refused and a third is accepted after an undo`() {
        val full = started().withDarts(DARTS_PER_VOLLEY)
        assertTrue(full.volleyFull)
        val refused = full.dart(clock, t20)
        assertEquals(CaptureRefusal.VOLLEY_FULL, refused.refusal)
        assertSame(full, refused.session)
        val undone = full.undo(clock).session
        assertFalse(undone.volleyFull)
        assertNull(undone.dart(clock, t20).refusal)
    }

    @Test
    fun `CP3 dart numbers keep counting voided entries and continue across volleys`() {
        val session = started().withDarts(1).undo(clock).session.withDarts(1).nextVolley(clock).session.withDarts(1)
        val numbers = session.data().volleys.flatMap { it.darts }.map { it.number }
        assertEquals(listOf(1, 2, 3), numbers)
        val outcome = session.dart(clock, t20)
        assertEquals(CaptureEffect.TakePhoto(4, "dart-004.jpg"), outcome.effects.single())
    }

    @Test
    fun `CP13 photo saved sets the photo, photo failed leaves it null and the session running`() {
        val session = started().withDarts(2)
        val saved = session.photoSaved(clock.advance(80), 1).session
        val darts = saved.data().volleys.single().darts
        assertEquals("dart-001.jpg", darts[0].photo?.file)
        assertNull(darts[1].photo)
        val outcome = saved.photoFailed(2)
        assertEquals(2, outcome.failedPhoto)
        val failed = outcome.session
        assertNull(failed.data().volleys.single().darts[1].photo)
        assertTrue(failed.running)
        assertNull(failed.dart(clock, t20).refusal)
    }

    @Test
    fun `CP13 photo saved for an unknown dart is ignored`() {
        val session = started()
        assertSame(session, session.photoSaved(clock, 7).session)
    }

    @Test
    fun `CP13 a photo saved after Stop is still recorded`() {
        val stopped = started().withDarts(1).stop(clock).session
        val saved = stopped.photoSaved(clock, 1).session
        assertNotNull(saved.data().volleys.single().darts.single().photo)
    }

    @Test
    fun `CP4 Undo voids the last non voided dart, keeps its photo and records the void stamp`() {
        val photographed = started().withDarts(2).photoSaved(clock, 2).session
        val voidTime = clock.advance(300).nowUtc()
        val undone = photographed.undo(clock).session
        val darts = undone.data().volleys.single().darts
        assertFalse(darts[0].voided)
        assertTrue(darts[1].voided)
        assertEquals("dart-002.jpg", darts[1].photo?.file)
        assertEquals(voidTime, darts[1].voidedAt?.utc)
        val again = undone.undo(clock).session
        assertTrue(again.data().volleys.single().darts.all { it.voided })
    }

    @Test
    fun `CP4 Undo is refused on an empty volley and on a previous volley after NextVolley`() {
        val empty = started()
        assertEquals(CaptureRefusal.NOTHING_TO_UNDO, empty.undo(clock).refusal)
        val next = started().withDarts(2).nextVolley(clock).session
        assertEquals(CaptureRefusal.NOTHING_TO_UNDO, next.undo(clock).refusal)
        assertEquals(2, next.data().volleys.first().darts.count { !it.voided })
    }

    @Test
    fun `CP5 NextVolley ends the volley, starts the next video and marks an empty volley`() {
        val outcome = started().nextVolley(clock.advance(1000))
        val volleys = outcome.session.data().volleys
        assertEquals(listOf(1, 2), volleys.map { it.index })
        assertNotNull(volleys[0].end)
        assertTrue(volleys[0].empty)
        assertNull(volleys[1].end)
        assertEquals("volley-002.mp4", volleys[1].video)
        assertEquals(
            listOf(CaptureEffect.StopVideo(1), CaptureEffect.StartVideo(2, "volley-002.mp4")),
            outcome.effects,
        )
    }

    @Test
    fun `CP6 Stop with an empty last volley keeps it, marked empty, with endReason STOP`() {
        val outcome = started().withDarts(1).nextVolley(clock).session.stop(clock.advance(5))
        val truth = outcome.session.data()
        assertEquals(EndReason.STOP, truth.endReason)
        assertNotNull(truth.endedAt)
        assertEquals(2, truth.volleys.size)
        assertTrue(truth.volleys.last().empty)
        assertNotNull(truth.volleys.last().end)
        assertEquals(listOf<CaptureEffect>(CaptureEffect.StopVideo(2)), outcome.effects)
        assertFalse(outcome.session.running)
    }

    @Test
    fun `CP6 a new session can start after Stop`() {
        val stopped = started().stop(clock).session
        assertNull(stopped.start(clock, setup, emptySet()).refusal)
    }

    @Test
    fun `CP9 Interrupt gives INTERRUPTED`() {
        val truth = started().withDarts(1).interrupt(clock).session.data()
        assertEquals(EndReason.INTERRUPTED, truth.endReason)
        assertNotNull(truth.volleys.last().end)
    }

    @Test
    fun `CP10 position is event minus video start, floored at 0`() {
        val session = started().let { it.videoStarted(clock.advance(200), 1).session }
        val dart = session.dart(clock.advance(1500), t20).session.data().volleys.single().darts.single()
        assertEquals(1500L, dart.entry.videoPositionMs)
        // A video start reported after the event time of the video clock: monotonic before the start gives 0.
        val early = CaptureSession(session.truth, mapOf(1 to clock.monotonicNanos() + 5_000_000_000L))
        val floored = early.dart(clock, t20).session.data().volleys.single().darts.single()
        assertEquals(0L, floored.entry.videoPositionMs)
    }

    @Test
    fun `CP10 position is null before the video Start event and stays null once it arrives`() {
        val early = started().dart(clock.advance(100), t20).session
        assertNull(early.data().volleys.single().darts.single().entry.videoPositionMs)
        val after = early.videoStarted(clock.advance(100), 1).session
        assertNull(after.data().volleys.single().darts.single().entry.videoPositionMs)
        val later = after.dart(clock.advance(50), t20).session.data().volleys.single().darts.last()
        assertEquals(50L, later.entry.videoPositionMs)
    }

    @Test
    fun `CP10 the volley end carries its video position and the first video start wins`() {
        val session = started().videoStarted(clock.advance(100), 1).session
        val again = session.videoStarted(clock.advance(900), 1).session
        val ended = again.nextVolley(clock.advance(400)).session.data().volleys
        assertEquals(1300L, ended[0].end?.videoPositionMs)
        assertNull(ended[1].start.videoPositionMs)
    }

    @Test
    fun `CP10 a photo is placed in the video of its own volley`() {
        val session = started().videoStarted(clock.advance(100), 1).session
            .dart(clock.advance(100), t20).session
            .nextVolley(clock.advance(100)).session
        val saved = session.photoSaved(clock.advance(100), 1).session
        assertEquals(300L, saved.data().volleys[0].darts.single().photo?.taken?.videoPositionMs)
    }
}
