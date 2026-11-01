package io.github.darkcosmos.kosmodarts.capture.truth

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import java.time.Instant

/** Sessions shared by the truth codec tests. */
internal object TruthFixtures {

    private val START: Instant = Instant.parse("2026-10-31T14:03:22.123Z")
    private const val START_NANOS = 5_000_000_000L
    private const val NANOS_PER_MILLI = 1_000_000L

    /** Stamp [offsetMs] after the session start on both clocks, with an optional video position. */
    fun at(offsetMs: Long, videoPositionMs: Long? = null): Stamp =
        Stamp(START.plusMillis(offsetMs), START_NANOS + offsetMs * NANOS_PER_MILLI, videoPositionMs)

    fun photo(number: Int, offsetMs: Long, videoPositionMs: Long?): TruthPhoto =
        TruthPhoto(file = "dart-%03d.jpg".format(number), taken = at(offsetMs, videoPositionMs))

    fun session(
        volleys: List<TruthVolley>,
        endedAt: Stamp? = null,
        endReason: EndReason? = null,
        device: DeviceInfo = DeviceInfo(manufacturer = "Google", model = "Pixel 7", sdk = 37),
        zoom: ZoomRecord = ZoomRecord(requestedRatio = 2.4f, ratio = 2.4f),
    ): TruthSession =
        TruthSession(
            session = "20261031-140322",
            app = AppInfo(id = "io.github.darkcosmos.kosmodarts.capture", version = "0.1.0"),
            device = device,
            startedAt = at(0),
            endedAt = endedAt,
            endReason = endReason,
            video = VideoSettings(VideoQuality.UHD, Resolution(width = 3840, height = 2160)),
            photo = PhotoSettings(Resolution(width = 4080, height = 3072), TimestampSource.REALTIME),
            zoom = zoom,
            volleys = volleys,
        )

    /**
     * Stopped session of 3 volleys (CP3 to CP6, CP13): T20 with photo and gravity, a voided 5 with its photo, a miss
     * with a null photo and null gravity; then 25 and bull, the bull recorded before the video start (null
     * positions); then an empty volley.
     */
    fun full(): TruthSession =
        session(
            volleys = listOf(
                TruthVolley(
                    index = 1, video = "volley-001.mp4", start = at(0), end = at(9_000, 8_800),
                    darts = listOf(
                        TruthDart(
                            1, Dart.triple(20), at(1_000, 800), null, photo(1, 1_050, 850), Gravity(0.1f, -9.8f, 0.25f),
                        ),
                        TruthDart(2, Dart.single(5), at(2_000, 1_800), at(2_500, 2_300), photo(2, 2_040, 1_840), null),
                        TruthDart(3, Dart.MISS, at(3_000, 2_800), null, null, null),
                    ),
                ),
                TruthVolley(
                    index = 2, video = "volley-002.mp4", start = at(9_000), end = at(20_000, 10_900),
                    darts = listOf(
                        TruthDart(4, Dart.BULL, at(9_010, null), null, photo(4, 9_020, null), null),
                        TruthDart(
                            5, Dart.OUTER_BULL, at(12_000, 2_900), null, photo(5, 12_030, 2_930),
                            Gravity(0f, -9.81f, 0f),
                        ),
                    ),
                ),
                TruthVolley(
                    index = 3, video = "volley-003.mp4", start = at(20_000), end = at(25_000, 4_900),
                    darts = emptyList(),
                ),
            ),
            endedAt = at(25_100),
            endReason = EndReason.STOP,
        )

    /** Session killed before Stop (CP9): no end, last volley running with one dart. */
    fun killed(): TruthSession =
        session(
            volleys = listOf(
                TruthVolley(
                    index = 1, video = "volley-001.mp4", start = at(0), end = at(5_000, 4_800),
                    darts = listOf(TruthDart(1, Dart.double(16), at(1_000, 800), null, photo(1, 1_030, 830), null)),
                ),
                TruthVolley(
                    index = 2, video = "volley-002.mp4", start = at(5_000), end = null,
                    darts = listOf(TruthDart(2, Dart.single(1), at(6_000, 900), null, null, Gravity(1f, 2f, 3f))),
                ),
            ),
        )
}
