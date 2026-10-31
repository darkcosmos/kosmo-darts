package io.github.darkcosmos.kosmodarts.capture.session

import io.github.darkcosmos.kosmodarts.capture.truth.AppInfo
import io.github.darkcosmos.kosmodarts.capture.truth.DeviceInfo
import io.github.darkcosmos.kosmodarts.capture.truth.EndReason
import io.github.darkcosmos.kosmodarts.capture.truth.Gravity
import io.github.darkcosmos.kosmodarts.capture.truth.PhotoSettings
import io.github.darkcosmos.kosmodarts.capture.truth.Stamp
import io.github.darkcosmos.kosmodarts.capture.truth.TruthDart
import io.github.darkcosmos.kosmodarts.capture.truth.TruthPhoto
import io.github.darkcosmos.kosmodarts.capture.truth.TruthSession
import io.github.darkcosmos.kosmodarts.capture.truth.TruthVolley
import io.github.darkcosmos.kosmodarts.capture.truth.VideoSettings
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import java.time.temporal.ChronoUnit

/** Darts a volley accepts (rule CP3). */
const val DARTS_PER_VOLLEY = 3

private const val NANOS_PER_MILLI = 1_000_000L

/** What the camera layer must do after an event. */
sealed interface CaptureEffect {
    /** Start recording the video of volley [volley] into [file]. */
    data class StartVideo(val volley: Int, val file: String) : CaptureEffect

    /** Stop and finalize the video of volley [volley]. */
    data class StopVideo(val volley: Int) : CaptureEffect

    /** Take the photo of dart entry [dart] into [file]. */
    data class TakePhoto(val dart: Int, val file: String) : CaptureEffect
}

/** Why an event was refused; the state is then unchanged. */
enum class CaptureRefusal { NO_SESSION, ALREADY_RUNNING, VOLLEY_FULL, NOTHING_TO_UNDO }

/** Result of an event: the new [session], the [effects] to perform, the [refusal] when it was not applied and the
 * [failedPhoto] dart number to report to the player (rule CP13). */
data class CaptureOutcome(
    val session: CaptureSession,
    val effects: List<CaptureEffect> = emptyList(),
    val refusal: CaptureRefusal? = null,
    val failedPhoto: Int? = null,
)

/** What a session records about the app, the phone and the camera, known at Start. */
data class CaptureSetup(
    val app: AppInfo,
    val device: DeviceInfo,
    val video: VideoSettings,
    val photo: PhotoSettings,
)

/**
 * State of the capture screen's session and its reducer (rules CP1 to CP7, CP9, CP10 and CP13). Every function takes
 * the event and a [CaptureClock] and returns a [CaptureOutcome]; nothing is mutated.
 *
 * [truth] is null before the first Start. [videoStartNanos] maps a volley index to the monotonic time of its video
 * `Start` event, once known (rule CP10).
 */
data class CaptureSession(
    val truth: TruthSession? = null,
    val videoStartNanos: Map<Int, Long> = emptyMap(),
) {
    /** Whether a session is running: started and not ended. */
    val running: Boolean get() = truth != null && truth.endedAt == null

    /** Non-voided darts of the current volley, 0 outside a session. */
    val currentVolleyDarts: Int get() = if (running) truth?.volleys?.last()?.darts?.count { !it.voided } ?: 0 else 0

    /** Whether the current volley is full (rule CP3): the keypad is then disabled. */
    val volleyFull: Boolean get() = running && currentVolleyDarts >= DARTS_PER_VOLLEY

    /** Rule CP1: start a session; [existingFolders] are the session folders of this installation. */
    fun start(clock: CaptureClock, setup: CaptureSetup, existingFolders: Set<String>): CaptureOutcome {
        if (running) return refused(CaptureRefusal.ALREADY_RUNNING)
        val now = clock.stamp()
        val first = TruthVolley(1, CaptureNaming.videoFile(1), now, null, emptyList())
        val folder = CaptureNaming.sessionFolder(now.utc, existingFolders)
        val created = TruthSession(
            folder, setup.app, setup.device, now, null, null, setup.video, setup.photo, listOf(first),
        )
        return CaptureOutcome(
            CaptureSession(created),
            listOf(CaptureEffect.StartVideo(1, first.video)),
        )
    }

    /** Rule CP10: the video of [volley] reported its `Start` event. Later events get a position. */
    fun videoStarted(clock: CaptureClock, volley: Int): CaptureOutcome {
        val current = truth
        if (current == null || current.volleys.none { it.index == volley } || volley in videoStartNanos) {
            return CaptureOutcome(this)
        }
        return CaptureOutcome(copy(videoStartNanos = videoStartNanos + (volley to clock.monotonicNanos())))
    }

    /** Rules CP2 and CP3: add a dart entry and take its photo. */
    fun dart(clock: CaptureClock, dart: Dart, gravity: Gravity? = null): CaptureOutcome {
        val current = openTruth() ?: return refused(CaptureRefusal.NO_SESSION)
        if (volleyFull) return refused(CaptureRefusal.VOLLEY_FULL)
        val volley = current.volleys.last()
        val number = current.volleys.sumOf { it.darts.size } + 1
        val entry = TruthDart(number, dart, clock.stampIn(volley.index), null, null, gravity)
        val updated = volley.copy(darts = volley.darts + entry)
        return CaptureOutcome(
            withVolleys(current.volleys.dropLast(1) + updated),
            listOf(CaptureEffect.TakePhoto(number, CaptureNaming.photoFile(number))),
        )
    }

    /** Rule CP13 and CP3: the photo of dart entry [number] was saved. Ignored when the entry does not exist. */
    fun photoSaved(clock: CaptureClock, number: Int): CaptureOutcome {
        val current = truth ?: return CaptureOutcome(this)
        val volley = current.volleys.firstOrNull { v -> v.darts.any { it.number == number } }
            ?: return CaptureOutcome(this)
        val photo = TruthPhoto(CaptureNaming.photoFile(number), clock.stampIn(volley.index))
        return CaptureOutcome(replaceDart(volley, number) { it.copy(photo = photo) })
    }

    /** Rule CP13: the photo of dart entry [number] failed; the dart keeps `photo` null and the session goes on. */
    fun photoFailed(number: Int): CaptureOutcome = CaptureOutcome(this, failedPhoto = number)

    /** Rule CP4: void the last non-voided dart of the current volley. */
    fun undo(clock: CaptureClock): CaptureOutcome {
        val current = openTruth() ?: return refused(CaptureRefusal.NO_SESSION)
        val volley = current.volleys.last()
        val target = volley.darts.lastOrNull { !it.voided } ?: return refused(CaptureRefusal.NOTHING_TO_UNDO)
        val voidedAt = clock.stampIn(volley.index)
        return CaptureOutcome(replaceDart(volley, target.number) { it.copy(voidedAt = voidedAt) })
    }

    /** Rule CP5: end the current volley and start the next one, with its video. */
    fun nextVolley(clock: CaptureClock): CaptureOutcome {
        val current = openTruth() ?: return refused(CaptureRefusal.NO_SESSION)
        val last = current.volleys.last()
        val now = clock.stamp()
        val ended = last.copy(end = now.copy(videoPositionMs = positionOf(last.index, now)))
        val next = TruthVolley(last.index + 1, CaptureNaming.videoFile(last.index + 1), now, null, emptyList())
        return CaptureOutcome(
            withVolleys(current.volleys.dropLast(1) + ended + next),
            listOf(CaptureEffect.StopVideo(last.index), CaptureEffect.StartVideo(next.index, next.video)),
        )
    }

    /** Rule CP6: confirmed Stop. */
    fun stop(clock: CaptureClock): CaptureOutcome = finish(clock, EndReason.STOP)

    /** Rule CP9: the screen really left or the camera failed. */
    fun interrupt(clock: CaptureClock): CaptureOutcome = finish(clock, EndReason.INTERRUPTED)

    private fun finish(clock: CaptureClock, reason: EndReason): CaptureOutcome {
        val current = openTruth() ?: return refused(CaptureRefusal.NO_SESSION)
        val last = current.volleys.last()
        val now = clock.stamp()
        val ended = last.copy(end = now.copy(videoPositionMs = positionOf(last.index, now)))
        val finished = current.copy(volleys = current.volleys.dropLast(1) + ended, endedAt = now, endReason = reason)
        return CaptureOutcome(copy(truth = finished), listOf(CaptureEffect.StopVideo(last.index)))
    }

    private fun openTruth(): TruthSession? = if (running) truth else null

    private fun refused(reason: CaptureRefusal) = CaptureOutcome(this, refusal = reason)

    private fun withVolleys(volleys: List<TruthVolley>): CaptureSession = copy(truth = truth?.copy(volleys = volleys))

    private fun replaceDart(volley: TruthVolley, number: Int, change: (TruthDart) -> TruthDart): CaptureSession {
        val darts = volley.darts.map { if (it.number == number) change(it) else it }
        val volleys = truth?.volleys.orEmpty().map { if (it.index == volley.index) volley.copy(darts = darts) else it }
        return withVolleys(volleys)
    }

    /** Position in the video of [volley] of an event at [stamp]: floored at 0, null before the video start is known. */
    private fun positionOf(volley: Int, stamp: Stamp): Long? =
        videoStartNanos[volley]?.let { maxOf(0L, stamp.monotonicNanos - it) / NANOS_PER_MILLI }

    private fun CaptureClock.stamp(): Stamp = Stamp(nowUtc().truncatedTo(ChronoUnit.MILLIS), monotonicNanos())

    private fun CaptureClock.stampIn(volley: Int): Stamp {
        val now = stamp()
        return now.copy(videoPositionMs = positionOf(volley, now))
    }
}
